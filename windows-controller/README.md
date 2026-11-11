# Windows Laser Controller

Run the simulator:

```bat
run-controller.cmd
```

Run the compiled simulator from `releases\\windows-controller\\run-controller.cmd`, or run the source version below. The installed corporate SDK builds the project as .NET 10 because that is the approved SDK available on this workstation.

Run the hardware transport only under the controlled validation plan:

```bat
dotnet run --project LaserController.Windows.csproj -- --hardware
```

The host binds only to `127.0.0.1`. It prints and opens a tokenized local URL. Do not expose its port through a proxy or firewall rule. The simulator is the default. Hardware mode requires the explicit `--hardware` argument.

The Windows application accepts typed controller actions only. It has no free-command endpoint.
