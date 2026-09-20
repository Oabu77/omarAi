"""Provider-free regression for Omar AI security workflow coverage."""

from pathlib import Path
import re
import unittest

import yaml


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


class UniqueKeyBaseLoader(yaml.BaseLoader):
    """YAML loader that keeps scalar strings and rejects duplicate mapping keys."""


def _construct_unique_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise AssertionError(f"duplicate YAML mapping key: {key}")
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


UniqueKeyBaseLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    _construct_unique_mapping,
)


def _load_workflow(text: str):
    """Parse using YAML semantics while preserving GitHub keys such as `on`."""
    loaded = yaml.load(text, Loader=UniqueKeyBaseLoader)
    if not isinstance(loaded, dict):
        raise AssertionError("workflow must parse to a YAML mapping")
    return loaded


def _walk_mapping_keys(value, path=()):
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = path + (str(key),)
            yield child_path, key, child
            yield from _walk_mapping_keys(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _walk_mapping_keys(child, path + (str(index),))


def _event_paths(text: str, event: str) -> set[str]:
    workflow = _load_workflow(text)
    on_block = workflow.get("on")
    if not isinstance(on_block, dict):
        raise AssertionError("workflow must contain an `on` mapping")
    event_block = on_block.get(event)
    if not isinstance(event_block, dict):
        raise AssertionError(f"missing {event} event mapping")
    paths = event_block.get("paths")
    if not isinstance(paths, list):
        raise AssertionError(f"missing {event} paths list")
    return {str(path) for path in paths}


def _assert_read_only_permissions(text: str) -> None:
    workflow = _load_workflow(text)
    assert workflow.get("permissions") == {"contents": "read"}, (
        "top-level permissions must be exactly contents: read"
    )
    permission_paths = [
        path
        for path, key, _ in _walk_mapping_keys(workflow)
        if key == "permissions"
    ]
    assert permission_paths == [("permissions",)], (
        "no job/step-level permissions key or override is allowed"
    )


def _checkout_refs(text: str) -> list[str]:
    workflow = _load_workflow(text)
    refs: list[str] = []
    for _, key, value in _walk_mapping_keys(workflow):
        if key != "uses" or not isinstance(value, str):
            continue
        action, separator, ref = value.partition("@")
        if separator and action.lower() == "actions/checkout":
            refs.append(ref)
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

    def test_yaml_escaped_permission_overrides_are_rejected(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        for spelling in (
            "permissions: write-all",
            "'permissions': write-all",
            '"permissions": write-all',
            '"permi\\u0073sions": write-all',
            '"permi\\x73sions": write-all',
        ):
            mutated = text.replace("  smoke:\n", f"  smoke:\n    {spelling}\n", 1)
            with self.subTest(spelling=spelling):
                with self.assertRaises(AssertionError):
                    _assert_read_only_permissions(mutated)

    def test_duplicate_permissions_key_is_rejected(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        mutated = text.replace(
            "permissions:\n  contents: read\n",
            "permissions:\n  contents: read\npermissions: write-all\n",
            1,
        )
        with self.assertRaises(AssertionError):
            _assert_read_only_permissions(mutated)

    def test_every_checkout_reference_is_immutable(self) -> None:
        _assert_checkout_refs_immutable(WORKFLOW.read_text(encoding="utf-8"))

    def test_yaml_escaped_checkout_references_are_checked(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        for step in (
            "      - uses: 'actions/checkout@main'\n",
            '      - uses: "actions/checkout@main"\n',
            '      - "uses": "actions/checkout@\\u006dain"\n',
            '      - "uses": "actions/checkout@\\x6dain"\n',
            "      - uses: Actions/Checkout@main\n",
        ):
            mutated = text.replace("    steps:\n", f"    steps:\n{step}", 1)
            with self.subTest(step=step.strip()):
                with self.assertRaises(AssertionError):
                    _assert_checkout_refs_immutable(mutated)

    def test_declared_dependencies_are_exercised_in_isolation(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m venv .venv", text)
        self.assertIn("--only-binary=:all: -r requirements.txt", text)
        self.assertIn("PyYAML==6.0.3", text)
        self.assertIn(".venv/bin/python -m pip check", text)
        self.assertNotIn("sudo pip", text)


if __name__ == "__main__":
    unittest.main()
