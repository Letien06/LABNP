import unittest

from mailbox_cache import load_messages


class MailboxCacheTest(unittest.TestCase):
    def setUp(self):
        self.retrieved = []

    def fetch(self, number, uid):
        self.retrieved.append((number, uid))
        return {"id": number, "uid": uid, "body": "Mail " + uid}

    def test_first_load_retrieves_all_messages(self):
        result = load_messages([b"1 first", b"2 second"], [], self.fetch)

        self.assertEqual(self.retrieved, [(1, "first"), (2, "second")])
        self.assertEqual([row["uid"] for row in result["messages"]], ["first", "second"])

    def test_refresh_reports_current_order_and_retrieves_only_new_messages(self):
        result = load_messages(
            [b"1 retained", b"2 new"], ["deleted", "retained"], self.fetch
        )

        self.assertEqual(self.retrieved, [(2, "new")])
        self.assertEqual(result["mailbox"], [{"id": 1, "uid": "retained"}, {"id": 2, "uid": "new"}])

    def test_unchanged_and_empty_mailboxes_do_not_retrieve_messages(self):
        self.assertEqual(load_messages([b"1 retained"], ["retained"], self.fetch)["messages"], [])
        self.assertEqual(load_messages([], ["retained"], self.fetch), {"mailbox": [], "messages": []})
        self.assertEqual(self.retrieved, [])

    def test_duplicate_uids_fall_back_to_full_load(self):
        result = load_messages([b"1 duplicate", b"2 duplicate"], ["duplicate"], self.fetch)

        self.assertIsNone(result["mailbox"])
        self.assertEqual(self.retrieved, [(1, "duplicate"), (2, "duplicate")])


if __name__ == "__main__":
    unittest.main()
