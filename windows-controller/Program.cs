using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading.Channels;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Storage.Streams;

var mode = args.Contains("--hardware", StringComparer.OrdinalIgnoreCase) ? TransportMode.Hardware : TransportMode.Simulator;
var port = ReadPort(args) ?? 48217;
var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
var builder = WebApplication.CreateBuilder(new WebApplicationOptions {
    Args = args,
    ContentRootPath = AppContext.BaseDirectory,
    WebRootPath = Path.Combine(AppContext.BaseDirectory, "wwwroot"),
});
builder.Logging.ClearProviders();
builder.Logging.AddConsole();
builder.WebHost.UseUrls($"http://127.0.0.1:{port}");
builder.Services.AddSingleton(new SessionAudit());
builder.Services.AddSingleton<ILaserTransport>(mode == TransportMode.Hardware ? new WindowsBleTransport() : new SimulatedTransport());
builder.Services.AddSingleton<Controller>();
var app = builder.Build();

app.Use(async (context, next) => {
    try { await next(); }
    catch (OperationCanceledException) when (context.RequestAborted.IsCancellationRequested) { }
    catch (Exception error) {
        context.RequestServices.GetRequiredService<Controller>().Invalidate();
        // Do not log request URLs because they contain the access token.
        Console.Error.WriteLine($"Request failed: {error.GetType().Name}");
        if (!context.Response.HasStarted) {
            context.Response.StatusCode = error is ArgumentException or BadHttpRequestException ? 400 : 503;
            await context.Response.WriteAsJsonAsync(new { error = "Request failed. Device state is unknown. Reconnect before continuing." });
        } else context.Abort();
    }
});

app.Use(async (context, next) => {
    if (context.Request.Path.StartsWithSegments("/api") || context.Request.Path.StartsWithSegments("/ws")) {
        if (!CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(context.Request.Query["token"].ToString()), Encoding.UTF8.GetBytes(token))) {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            await context.Response.WriteAsync("Unauthorized local controller request.");
            return;
        }
    }
    await next();
});
app.UseWebSockets();
app.UseDefaultFiles();
app.UseStaticFiles();

app.MapGet("/api/status", (Controller controller) => Results.Ok(controller.Snapshot()));
app.MapPost("/api/scan", async (Controller controller) => Results.Ok(await controller.ScanAsync()));
app.MapPost("/api/connect/{address}", async (string address, Controller controller) => Results.Ok(await controller.ConnectAsync(address)));
app.MapPost("/api/command/{name}", async (string name, CommandRequest request, Controller controller) =>
    Results.Ok(await controller.ExecuteAsync(name, request)));
app.MapPost("/api/observation", (ObservationRequest request, Controller controller) => Results.Ok(controller.RecordObservation(request.Note)));
app.MapGet("/api/audit/export", (Controller controller) => Results.File(controller.ExportAudit(), "application/zip", "laser-controller-session.zip"));
app.Map("/ws/status", async context => {
    if (!context.WebSockets.IsWebSocketRequest) { context.Response.StatusCode = 400; return; }
    using var socket = await context.WebSockets.AcceptWebSocketAsync();
    var controller = context.RequestServices.GetRequiredService<Controller>();
    await foreach (var snapshot in controller.StatusUpdates(context.RequestAborted)) {
        await socket.SendAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(snapshot)), System.Net.WebSockets.WebSocketMessageType.Text, true, context.RequestAborted);
    }
});

Console.WriteLine($"Laser Controller ({mode})");
Console.WriteLine($"Session: {token[..12]}");
Console.WriteLine($"URL: http://127.0.0.1:{port}/?token={token}");
if (!args.Contains("--no-browser", StringComparer.OrdinalIgnoreCase)) Process.Start(new ProcessStartInfo($"http://127.0.0.1:{port}/?token={token}") { UseShellExecute = true });
await app.RunAsync();

static int? ReadPort(string[] args) {
    var index = Array.FindIndex(args, value => value.Equals("--port", StringComparison.OrdinalIgnoreCase));
    return index >= 0 && index + 1 < args.Length && int.TryParse(args[index + 1], out var port) && port is > 1024 and < 65536 ? port : null;
}

enum TransportMode { Simulator, Hardware }
enum CommandKind { Identify, ReadStatus, Enable, Disable, ApplyParameters, Waveform }
record LaserCandidate(string Name, string Address, int Rssi);
record DeviceSnapshot(bool Connected, bool Verified, bool OutputEnabled, string SessionId, string Mode, string Message, IReadOnlyDictionary<string, double> Applied, IReadOnlyList<LaserCandidate> Candidates);
record CommandRequest(Dictionary<string, double>? Values, int? Waveform, bool ExperimentalLiveTuning, string? Language);
record ObservationRequest(string Note);
record TransportReply(bool Accepted, bool OutputEnabled, Dictionary<string, double> Applied, IReadOnlyList<string> Tx, IReadOnlyList<string> Rx, string Message);

interface ILaserTransport {
    Task<IReadOnlyList<LaserCandidate>> ScanAsync(CancellationToken cancellationToken);
    Task ConnectAsync(string address, CancellationToken cancellationToken);
    Task DisconnectAsync();
    Task<TransportReply> ExecuteAsync(CommandKind command, Dictionary<string, double>? values, int? waveform, CancellationToken cancellationToken);
    event Action<string, byte[]>? Traffic;
}

sealed class Controller {
    private readonly ILaserTransport transport;
    private readonly SessionAudit audit;
    private readonly Channel<DeviceSnapshot> updates = Channel.CreateUnbounded<DeviceSnapshot>();
    private readonly SemaphoreSlim commandLock = new(1, 1);
    private readonly Dictionary<string, double> applied = new() { ["output_percent"] = 20, ["pwm_frequency_hz"] = 1000, ["duty_cycle_percent"] = 20 };
    private List<LaserCandidate> candidates = [];
    private string sessionId = Guid.NewGuid().ToString();
    private bool connected, verified, outputEnabled;
    private string message = "DISCONNECTED";
    public Controller(ILaserTransport transport, SessionAudit audit) {
        this.transport = transport;
        this.audit = audit;
        transport.Traffic += (direction, bytes) => audit.Traffic(sessionId, direction, bytes);
    }
    public void Invalidate() { verified = false; Publish("DEVICE STATE IS UNKNOWN. RECONNECT BEFORE CONTINUING."); }
    public DeviceSnapshot Snapshot() => new(connected, verified, outputEnabled, sessionId, transport.GetType().Name, message, new Dictionary<string, double>(applied), candidates.ToArray());
    private DeviceSnapshot Publish(string next) { message = next; var status = Snapshot(); updates.Writer.TryWrite(status); return status; }
    public async Task<DeviceSnapshot> ScanAsync() {
        candidates = (await transport.ScanAsync(CancellationToken.None)).ToList();
        if (candidates.Count == 1) return await ConnectAsync(candidates[0].Address);
        return Publish(candidates.Count == 0 ? "NO COMPATIBLE BWT LASER FOUND" : "SELECT A LASER");
    }
    public async Task<DeviceSnapshot> ConnectAsync(string address) {
        await transport.ConnectAsync(address, CancellationToken.None);
        connected = true; verified = false; outputEnabled = false; sessionId = Guid.NewGuid().ToString(); audit.Start(sessionId, transport.GetType().Name);
        var reply = await SendAsync(CommandKind.Identify, null, null, "VERIFY DEVICE IDENTITY");
        verified = reply.Accepted; return Publish(verified ? "DEVICE VERIFIED" : "DEVICE IDENTITY NOT VERIFIED");
    }
    public async Task<DeviceSnapshot> ExecuteAsync(string action, CommandRequest request) {
        if (!Enum.GetNames<CommandKind>().Contains(action, StringComparer.OrdinalIgnoreCase) || !Enum.TryParse<CommandKind>(action, true, out var command)) return Publish("COMMAND NOT ALLOWED");
        if (command == CommandKind.ApplyParameters && request.Values is not { Count: > 0 }) return Publish("PARAMETER VALUES ARE REQUIRED");
        if (command == CommandKind.Waveform && request.Waveform is not (>= 0 and <= 8)) return Publish("WAVEFORM VALUE IS INVALID");
        if (!connected || !verified) return Publish("CURRENT DEVICE IDENTITY IS NOT VERIFIED");
        if (command == CommandKind.ApplyParameters && request.Values is { Count: > 0 }) {
            var keys = request.Values.Keys.ToHashSet();
            var allowed = new HashSet<string> { "output_percent", "pwm_frequency_hz", "duty_cycle_percent" };
            if (!keys.IsSubsetOf(allowed)) return Publish("PARAMETER IS NOT ALLOWLISTED");
            if (outputEnabled && !keys.All(key => allowed.Contains(key))) return Publish("LIVE PARAMETER IS NOT ALLOWLISTED");
            foreach (var pair in request.Values) if (!InRange(pair.Key, pair.Value)) return Publish("PARAMETER VALUE IS OUTSIDE ITS ALLOWED RANGE");
        }
        if (command == CommandKind.Waveform && outputEnabled) return Publish("WAVEFORM CHANGES ARE NOT AVAILABLE DURING ACTIVE OUTPUT");
        var reply = await SendAsync(command, request.Values, request.Waveform, action.ToUpperInvariant());
        if (reply.Accepted) { outputEnabled = reply.OutputEnabled; foreach (var value in reply.Applied) applied[value.Key] = value.Value; }
        return Publish(reply.Message);
    }
    private async Task<TransportReply> SendAsync(CommandKind command, Dictionary<string, double>? values, int? waveform, string action) {
        await commandLock.WaitAsync();
        try {
            var before = Snapshot(); var started = Stopwatch.GetTimestamp();
            var reply = await transport.ExecuteAsync(command, values, waveform, CancellationToken.None);
            audit.Command(sessionId, action, before, reply, Stopwatch.GetElapsedTime(started));
            return reply;
        } catch { Invalidate(); throw; }
        finally { commandLock.Release(); }
    }
    public DeviceSnapshot RecordObservation(string note) { if (string.IsNullOrWhiteSpace(note) || note.Length > 4000) throw new ArgumentException("Observation must contain 1 to 4000 characters."); audit.Observation(sessionId, note); return Publish("OBSERVATION RECORDED"); }
    public string ExportAudit() => audit.Export(sessionId, Snapshot());
    public async IAsyncEnumerable<DeviceSnapshot> StatusUpdates([System.Runtime.CompilerServices.EnumeratorCancellation] CancellationToken cancellationToken) {
        yield return Snapshot(); while (await updates.Reader.WaitToReadAsync(cancellationToken)) while (updates.Reader.TryRead(out var update)) yield return update;
    }
    private static bool InRange(string key, double value) => key switch { "output_percent" => value is >= 0 and <= 100, "pwm_frequency_hz" => value is >= 1 and <= 5000, "duty_cycle_percent" => value is >= 1 and <= 99, _ => false };
}

static class LegacyProtocol {
    public static IReadOnlyList<string> Lines(CommandKind command, Dictionary<string, double>? values, int? waveform) => command switch {
        CommandKind.Identify => ["RBWT"], CommandKind.ReadStatus => ["REN", "RCPR", "RCMF", "RCDC", "RCMFH", "RCDCL"],
        CommandKind.Enable => ["WEN Y", "REN"], CommandKind.Disable => ["WEN N", "REN"],
        CommandKind.Waveform when waveform is >= 0 and <= 8 => [$"RNWAVE {waveform}"],
        CommandKind.ApplyParameters => ParameterLines(values ?? throw new ArgumentException("Values are required.")), _ => throw new InvalidOperationException("Command is not allowlisted."),
    };
    private static IReadOnlyList<string> ParameterLines(Dictionary<string, double> values) {
        var lines = new List<string>();
        if (values.TryGetValue("output_percent", out var power)) { lines.Add($"WCPR {Format(power)}"); lines.Add("RCPR"); }
        if (values.TryGetValue("pwm_frequency_hz", out var frequency)) { lines.Add($"WCMF {Format(frequency)}"); lines.Add("RCMF"); }
        if (values.TryGetValue("duty_cycle_percent", out var duty)) { lines.Add($"WCDC {Format(duty)}"); lines.Add("RCDC"); }
        return lines;
    }
    public static IReadOnlyList<byte[]> Segments(string line) => Encoding.ASCII.GetBytes(line + "\r\n").Chunk(14).Select(x => x.ToArray()).ToList();
    private static string Format(double value) => value % 1 == 0 ? ((long)value).ToString() : value.ToString(System.Globalization.CultureInfo.InvariantCulture);
}

sealed class SimulatedTransport : ILaserTransport {
    private readonly Dictionary<string, double> values = new() { ["output_percent"] = 20, ["pwm_frequency_hz"] = 1000, ["duty_cycle_percent"] = 20 };
    private bool output;
    public event Action<string, byte[]>? Traffic;
    public Task<IReadOnlyList<LaserCandidate>> ScanAsync(CancellationToken _) => Task.FromResult<IReadOnlyList<LaserCandidate>>([new("BWT SIMULATED LASER", "SIM-0001", -42)]);
    public Task ConnectAsync(string _, CancellationToken __) => Task.CompletedTask;
    public Task DisconnectAsync() => Task.CompletedTask;
    public Task<TransportReply> ExecuteAsync(CommandKind command, Dictionary<string, double>? requested, int? waveform, CancellationToken _) {
        var tx = LegacyProtocol.Lines(command, requested, waveform); foreach (var line in tx) foreach (var bytes in LegacyProtocol.Segments(line)) Traffic?.Invoke("TX", bytes);
        output = command switch { CommandKind.Enable => true, CommandKind.Disable => false, _ => output };
        var rx = command switch { CommandKind.Identify => new[] { "BWTLASER" }, CommandKind.Enable => new[] { "REN 1" }, CommandKind.Disable => new[] { "REN 0" }, _ => Array.Empty<string>() };
        if (requested != null) foreach (var pair in requested) values[pair.Key] = pair.Value;
        foreach (var line in rx) Traffic?.Invoke("RX", Encoding.ASCII.GetBytes(line + "\r\n"));
        return Task.FromResult(new TransportReply(true, output, values.ToDictionary(), tx, rx, "COMMAND ACKNOWLEDGED"));
    }
}

sealed class WindowsBleTransport : ILaserTransport {
    private BluetoothLEDevice? device; private GattCharacteristic? characteristic; private readonly Channel<string> responses = Channel.CreateUnbounded<string>();
    public event Action<string, byte[]>? Traffic;
    public async Task<IReadOnlyList<LaserCandidate>> ScanAsync(CancellationToken cancellationToken) {
        var found = new ConcurrentDictionary<ulong, LaserCandidate>(); var watcher = new BluetoothLEAdvertisementWatcher();
        watcher.Received += (_, eventArgs) => { var name = eventArgs.Advertisement.LocalName; if (name.Contains("BWT", StringComparison.OrdinalIgnoreCase)) found[eventArgs.BluetoothAddress] = new(name, eventArgs.BluetoothAddress.ToString("X12"), eventArgs.RawSignalStrengthInDBm); };
        watcher.Start(); try { await Task.Delay(TimeSpan.FromSeconds(5), cancellationToken); } finally { watcher.Stop(); } return found.Values.OrderByDescending(x => x.Rssi).ToList();
    }
    public async Task ConnectAsync(string address, CancellationToken cancellationToken) {
        await DisconnectAsync(); var bluetoothAddress = Convert.ToUInt64(address.Replace(":", ""), 16); device = await BluetoothLEDevice.FromBluetoothAddressAsync(bluetoothAddress) ?? throw new InvalidOperationException("Unable to connect to the selected laser.");
        var services = await device.GetGattServicesAsync(BluetoothCacheMode.Uncached); var service = services.Services.FirstOrDefault(x => x.Uuid.ToString().StartsWith("0000fff0", StringComparison.OrdinalIgnoreCase)) ?? throw new InvalidOperationException("FFF0 service not found.");
        var characteristics = await service.GetCharacteristicsAsync(BluetoothCacheMode.Uncached); characteristic = characteristics.Characteristics.FirstOrDefault(x => x.Uuid.ToString().StartsWith("0000fff6", StringComparison.OrdinalIgnoreCase)) ?? throw new InvalidOperationException("FFF6 characteristic not found.");
        characteristic.ValueChanged += (_, eventArgs) => { var reader = DataReader.FromBuffer(eventArgs.CharacteristicValue); var bytes = new byte[reader.UnconsumedBufferLength]; reader.ReadBytes(bytes); Receive(bytes); }; var notify = await characteristic.WriteClientCharacteristicConfigurationDescriptorAsync(GattClientCharacteristicConfigurationDescriptorValue.Notify); if (notify != GattCommunicationStatus.Success) throw new InvalidOperationException("Unable to subscribe to laser notifications.");
    }
    public Task DisconnectAsync() { characteristic = null; device?.Dispose(); device = null; return Task.CompletedTask; }
    public async Task<TransportReply> ExecuteAsync(CommandKind command, Dictionary<string, double>? values, int? waveform, CancellationToken cancellationToken) {
        if (characteristic is null) return new(false, false, [], [], [], "BLUETOOTH DISCONNECTED"); var tx = LegacyProtocol.Lines(command, values, waveform).ToList(); var rx = new List<string>();
        foreach (var line in tx) { foreach (var segment in LegacyProtocol.Segments(line)) { Traffic?.Invoke("TX", segment); var writer = new DataWriter(); writer.WriteBytes(segment); var status = await characteristic.WriteValueWithResultAsync(writer.DetachBuffer(), GattWriteOption.WriteWithResponse); if (status.Status != GattCommunicationStatus.Success) return new(false, false, [], tx, rx, "WRITE NOT CONFIRMED"); } var expected = Expected(line); if (expected != null) { var response = await WaitForAsync(expected, cancellationToken); if (response == null) return new(false, false, [], tx, rx, "RESPONSE TIMEOUT"); rx.Add(response); } }
        var output = rx.Any(line => line.StartsWith("REN") && line.EndsWith("1")); return new(true, output, values ?? [], tx, rx, "COMMAND ACKNOWLEDGED");
    }
    private async Task<string?> WaitForAsync(string expected, CancellationToken cancellationToken) { using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken); timeout.CancelAfter(TimeSpan.FromSeconds(5)); while (await responses.Reader.WaitToReadAsync(timeout.Token)) while (responses.Reader.TryRead(out var response)) if (response == "BWTLASER" || response.StartsWith(expected, StringComparison.OrdinalIgnoreCase)) return response; return null; }
    private void Receive(byte[] bytes) { Traffic?.Invoke("RX", bytes); var text = Encoding.ASCII.GetString(bytes); foreach (var line in text.Split("\r\n", StringSplitOptions.RemoveEmptyEntries)) responses.Writer.TryWrite(line.Trim()); }
    private static string? Expected(string line) => line switch { "RBWT" => "BWTLASER", "REN" => "REN", "RCPR" => "RCPR", "RCMF" => "RCMF", "RCDC" => "RCDC", "RCMFH" => "RCMFH", "RCDCL" => "RCDCL", _ => null };
}

sealed class SessionAudit {
    private readonly string directory = Environment.GetEnvironmentVariable("SAFE_LASER_AUDIT_DIR")
        ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "SafeLaserController", "audit");
    private string? path;
    public void Start(string sessionId, string mode) { Directory.CreateDirectory(directory); path = Path.Combine(directory, $"session-{DateTimeOffset.UtcNow:yyyyMMddTHHmmss}-{sessionId}.jsonl"); Write(new { type = "session", wallClockUtc = DateTimeOffset.UtcNow, sessionId, mode }); }
    public void Traffic(string sessionId, string direction, byte[] bytes) => Write(new { type = "traffic", wallClockUtc = DateTimeOffset.UtcNow, monotonicTicks = Stopwatch.GetTimestamp(), sessionId, direction, text = Encoding.ASCII.GetString(bytes), hex = Convert.ToHexString(bytes) });
    public void Command(string sessionId, string action, DeviceSnapshot before, TransportReply reply, TimeSpan elapsed) => Write(new { type = "command", wallClockUtc = DateTimeOffset.UtcNow, monotonicTicks = Stopwatch.GetTimestamp(), sessionId, action, before, reply, elapsedMilliseconds = elapsed.TotalMilliseconds });
    public void Observation(string sessionId, string note) => Write(new { type = "observation", wallClockUtc = DateTimeOffset.UtcNow, sessionId, note = note.ReplaceLineEndings(" ") });
    public string Export(string sessionId, DeviceSnapshot snapshot) { if (path is null) throw new InvalidOperationException("No audit session exists."); var zip = Path.ChangeExtension(path, ".zip"); using var archive = ZipFile.Open(zip, ZipArchiveMode.Create); archive.CreateEntryFromFile(path, Path.GetFileName(path)); var summary = archive.CreateEntry("summary.json"); using var writer = new StreamWriter(summary.Open()); writer.Write(JsonSerializer.Serialize(new { sessionId, snapshot }, new JsonSerializerOptions { WriteIndented = true })); return zip; }
    private void Write(object record) { if (path is null) return; File.AppendAllText(path, JsonSerializer.Serialize(record) + Environment.NewLine, Encoding.UTF8); }
}
