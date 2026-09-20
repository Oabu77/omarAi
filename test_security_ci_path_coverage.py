"""Provider-free regression for Omar AI security workflow coverage."""

from pathlib import Path
import json
import re
import unittest


WORKFLOW = Path(".github/workflows/runner-smoke.yml")
CRITICAL_RUNTIME_PATHS = (
    "app.py",
    "config.py",
    "live_data.py",
    "requirements.txt",
    "system_prompt.md",
    "test_security_error_redaction.py",
    "test_security_play_publisher_readiness.py",
    "test_security_status_integrity.py",
    "test_security_ci_path_coverage.py",
    ".github/workflows/play-publisher-readiness.yml",
    ".github/workflows/runner-smoke.yml",
)


_YAML_KEY = r'''(?:"(?:\\.|[^"\\])*"|'(?:''|[^'])*'|[A-Za-z0-9_.-]+)'''


def _decode_yaml_scalar(token: str) -> str:
    """Resolve the scalar spellings relevant to workflow keys/uses values."""
    token = token.strip()
    if len(token) >= 2 and token[0] == token[-1] == '"':
        # JSON and YAML share the Unicode escape form used by GitHub workflow
        # keys/values (for example "permi\u0073sions").
        return json.loads(token)
    if len(token) >= 2 and token[0] == token[-1] == "'":
        # YAML single-quoted scalars escape a quote by doubling it.
        return token[1:-1].replace("''", "'")
    return token


def _mapping_entries(text: str) -> list[tuple[str, str, str]]:
    """Return (indent, resolved key, raw value) for block/list mappings."""
    entries: list[tuple[str, str, str]] = []
    pattern = re.compile(
        rf"^([ \t]*)(?:-\s+)?(?P<key>{_YAML_KEY})\s*:\s*(?P<value>.*)$"
    )
    for line in text.splitlines():
        match = pattern.match(line)
        if not match:
            continue
        try:
            key = _decode_yaml_scalar(match.group("key"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            # A malformed quoted scalar should not be treated as a trusted key.
            key = match.group("key")
        entries.append((match.group(1), key, match.group("value")))
    return entries


def _event_paths(text: str, event: str) -> set[str]:
    marker = f"  {event}:\n    paths:\n"
    if marker not in text:
        raise AssertionError(f"missing {event} paths block")

    paths: set[str] = set()
    remainder = text.split(marker, 1)[1]
    for line in remainder.splitlines():
        if line and not line.startswith(" "):
            break
        if re.match(r"^  [^\s].*:\s*$", line):
            break
        item = re.match(r"^\s{6}-\s+(.+?)\s*$", line)
        if item:
            paths.add(item.group(1))
    return paths


def _top_level_permissions_block(text: str) -> str:
    match = re.search(r"(?ms)^permissions:\n(?P<body>(?:  [^\n]+\n)+)\njobs:", text)
    if not match:
        raise AssertionError("missing top-level permissions block before jobs")
    return match.group("body")


def _permission_key_indents(text: str) -> list[str]:
    """Return indentation for every YAML key that resolves to `permissions`."""
    return [indent for indent, key, _ in _mapping_entries(text) if key == "permissions"]


def _assert_read_only_permissions(text: str) -> None:
    assert _top_level_permissions_block(text) == "  contents: read\n"
    assert _permission_key_indents(text) == [""], (
        "no job/step-level permissions key or scalar override is allowed"
    )


def _checkout_refs(text: str) -> list[str]:
    refs: list[str] = []
    for _, key, raw_value in _mapping_entries(text):
        if key != "uses":
            continue
        value = raw_value.strip()
        if not value:
            continue
        if value[0] in {'"', "'"}:
            quote = value[0]
            if quote == '"':
                match = re.match(r'^"(?:\\.|[^"\\])*"', value)
            else:
                match = re.match(r"^'(?:''|[^'])*'", value)
            if not match:
                continue
            try:
                value = _decode_yaml_scalar(match.group(0))
            except (json.JSONDecodeError, UnicodeDecodeError):
                continue
        else:
            value = re.split(r"\s+#", value, maxsplit=1)[0].strip()
        if value.startswith("actions/checkout@"):
            refs.append(value.removeprefix("actions/checkout@"))
    return refs


def _assert_checkout_refs_immutable(text: str) -> None:
    checkout_refs = _checkout_refs(text)
    assert checkout_refs, "workflow must contain actions/checkout"
    for ref in checkout_refs:
        assert re.fullmatch(r"[0-9a-f]{40}", ref), (
            f"checkout ref must be immutable: {ref}"
        )


class SecurityWorkflowCoverageTests(unittest.TestCase):
    def test_security_relevant_paths_trigger_each_event(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        for event in ("push", "pull_request"):
            paths = _event_paths(text, event)
            for path in CRITICAL_RUNTIME_PATHS:
                self.assertIn(path, paths, f"{path} must trigger {event} security runs")

    def test_workflow_has_only_read_only_top_level_permissions(self) -> None:
        _assert_read_only_permissions(WORKFLOW.read_text(encoding="utf-8"))

    def test_nested_scalar_permission_spellings_are_rejected(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        for spelling in (
            "permissions: write-all",
            "'permissions': write-all",
            '"permissions": write-all',
            '"permi\\u0073sions": write-all',
        ):
            mutated = text.replace("  smoke:\n", f"  smoke:\n    {spelling}\n", 1)
            with self.subTest(spelling=spelling):
                with self.assertRaises(AssertionError):
                    _assert_read_only_permissions(mutated)

    def test_every_checkout_reference_is_immutable(self) -> None:
        _assert_checkout_refs_immutable(WORKFLOW.read_text(encoding="utf-8"))

    def test_quoted_checkout_references_are_checked(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        for step in (
            "      - uses: 'actions/checkout@main'\n",
            '      - uses: "actions/checkout@main"\n',
            '      - "uses": "actions/checkout@\\u006dain"\n',
        ):
            mutated = text.replace("    steps:\n", f"    steps:\n{step}", 1)
            with self.subTest(step=step.strip()):
                with self.assertRaises(AssertionError):
                    _assert_checkout_refs_immutable(mutated)

    def test_declared_dependencies_are_exercised_in_isolation(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m venv .venv", text)
        self.assertIn("--only-binary=:all: -r requirements.txt", text)
        self.assertIn(".venv/bin/python -m pip check", text)
        self.assertNotIn("sudo pip", text)


if __name__ == "__main__":
    unittest.main()
