import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/worktree_owner.py"
SPEC = importlib.util.spec_from_file_location("worktree_owner", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
worktree_owner = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = worktree_owner
SPEC.loader.exec_module(worktree_owner)


class WorktreeOwnerTests(unittest.TestCase):
    def test_expected_issue_reads_task_branch(self):
        self.assertEqual(
            worktree_owner.expected_issue("codex/59-project-reorganization"), 59
        )

    def test_expected_issue_rejects_non_task_branch(self):
        self.assertIsNone(worktree_owner.expected_issue("main"))
        self.assertIsNone(worktree_owner.expected_issue("codex/not-an-issue"))

    def test_claim_rejects_empty_owner_before_git_lookup(self):
        with self.assertRaisesRegex(ValueError, "owner"):
            worktree_owner.claim(59, "")

    def test_unclaim_requires_clean_main_at_origin_tree(self):
        worktree_owner.validate_unclaim("main", False, "tree", "tree")
        with self.assertRaisesRegex(ValueError, "switching to main"):
            worktree_owner.validate_unclaim("codex/59-task", False, "tree", "tree")
        with self.assertRaisesRegex(ValueError, "clean"):
            worktree_owner.validate_unclaim("main", True, "tree", "tree")
        with self.assertRaisesRegex(ValueError, "origin/main"):
            worktree_owner.validate_unclaim("main", False, "tree", "other")


if __name__ == "__main__":
    unittest.main()
