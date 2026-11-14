"""Schemathesis hooks for the entitlements API-contract gate.

Loaded via ``hooks = "./hooks/hooks.py"`` in ``schemathesis.toml``.
"""

from __future__ import annotations

import schemathesis

# Schemathesis runs always use data-partition-id ``osdu`` / app.domain ``group``.
# Avoid bootstrap local-part ``users`` (DELETE is rejected for those groups).
_PARTITION_EMAIL = "schemathesis@osdu.group"
_PATH_EMAIL_KEYS = ("group_email", "member_email")


def _align_path_emails(case) -> None:
    """Rewrite path emails to the fixed osdu partition domain email.

    OpenAPI only has a generic email pattern, but runtime
    ``ApiInputValidation.validateEmailAndBelongsToPartition`` requires
    ``@osdu.group``.

    Must run via ``map_case`` / ``before_add_examples``: coverage and examples
    do not apply ``map_path_parameters`` (that hook only wraps Hypothesis
    strategies used by fuzzing).
    """
    path_parameters = case.path_parameters
    if not path_parameters:
        return
    for key in _PATH_EMAIL_KEYS:
        if key in path_parameters:
            path_parameters[key] = _PARTITION_EMAIL


@schemathesis.hook("map_case")
def align_path_emails_on_case(context, case):
    """Rewrite path emails after case materialization (coverage + fuzzing)."""
    _align_path_emails(case)
    return case


@schemathesis.hook("before_add_examples")
def align_path_emails_on_examples(context, examples):
    """Rewrite path emails in examples-phase cases (no map_case there)."""
    for case in examples:
        _align_path_emails(case)


@schemathesis.hook("map_path_parameters")
def align_path_emails_to_partition_domain(context, path_parameters):
    """Rewrite path emails during Hypothesis strategy generation (fuzzing)."""
    if not path_parameters:
        return path_parameters
    updated = dict(path_parameters)
    for key in _PATH_EMAIL_KEYS:
        if key in updated:
            updated[key] = _PARTITION_EMAIL
    return updated
