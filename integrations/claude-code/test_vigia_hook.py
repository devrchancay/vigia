import json
import os
import tempfile
import unittest
from unittest import mock

import vigia_hook


class DecideTest(unittest.TestCase):
    def payload(self, event, **extra):
        return {"hook_event_name": event, "session_id": "abc", "cwd": "/Users/me/code/vigia", **extra}

    def test_prompt_marks_working_with_the_prompt_as_body(self):
        kind, title, body, level = vigia_hook.decide(self.payload("UserPromptSubmit", prompt="fix   the\nbuild"))
        self.assertEqual((kind, title, body, level), ("working", "vigia: working", "fix the build", "info"))

    def test_permission_notification_waits(self):
        decision = vigia_hook.decide(self.payload("Notification", message="Claude needs your permission to use Bash"))
        self.assertEqual(decision[0], "waiting_permission")
        self.assertEqual(decision[3], "warn")
        typed = vigia_hook.decide(self.payload("Notification", message="x", notification_type="permission_prompt"))
        self.assertEqual(typed[0], "waiting_permission")

    def test_idle_notification_and_stop_are_done(self):
        self.assertEqual(vigia_hook.decide(self.payload("Notification", message="Claude is waiting for your input"))[0], "done")
        self.assertEqual(vigia_hook.decide(self.payload("Stop"))[1], "vigia: done")

    def test_session_end_deletes_and_unknown_events_do_nothing(self):
        self.assertEqual(vigia_hook.decide(self.payload("SessionEnd"))[0], "delete")
        self.assertIsNone(vigia_hook.decide(self.payload("PreCompact")))


class RequestTest(unittest.TestCase):
    def test_post_event_with_ttl_and_body(self):
        request = vigia_hook.build_request("http://p:8787", "t", "claude-code", "abc", ("working", "vigia: working", "x", "info"))
        self.assertEqual(request.full_url, "http://p:8787/api/v1/events")
        self.assertEqual(request.get_header("Authorization"), "Bearer t")
        body = json.loads(request.data)
        self.assertEqual(body["key"], "abc")
        self.assertEqual(body["ttlSec"], 7200)

    def test_delete_targets_the_session(self):
        request = vigia_hook.build_request("http://p:8787", "t", "claude-code", "a/b", ("delete", "vigia", None, None))
        self.assertEqual(request.get_method(), "DELETE")
        self.assertEqual(request.full_url, "http://p:8787/api/v1/events/claude-code/a%2Fb")


class ChangedTest(unittest.TestCase):
    def test_only_state_changes_are_sent(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(vigia_hook, "STATE_DIR", tmp):
            self.assertTrue(vigia_hook.changed("s1", "working"))
            self.assertFalse(vigia_hook.changed("s1", "working"))
            self.assertTrue(vigia_hook.changed("s1", "done"))
            self.assertTrue(vigia_hook.changed("s1", "delete"))
            self.assertFalse(os.path.exists(os.path.join(tmp, "s1")))


if __name__ == "__main__":
    unittest.main()
