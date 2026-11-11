import importlib.util
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "scripts" / "text_lint.py"
SPEC = importlib.util.spec_from_file_location("text_lint", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


def test_operator_resources_pass_lint():
    root = Path(__file__).parents[1]
    errors = [error for source, text in MODULE.operator_strings(root) for error in MODULE.lint(source, text)]
    assert errors == []


def test_prohibited_synonym_is_found():
    assert MODULE.lint("test", "The laser is ready.")


def test_long_instruction_is_found():
    text = "Use the physical emergency stop and then immediately ask another operator to complete every remaining shutdown step in the approved procedure."
    assert MODULE.lint("test", text)

