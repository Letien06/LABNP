"""Use a POP3 UIDL snapshot to refresh a client-side message cache."""


def load_messages(uidl_lines, known_uids, fetch_message):
    """Return the current message order and fetch rows absent from the client."""
    mailbox = []
    for line in uidl_lines:
        number, uid = line.decode("ascii").split()
        mailbox.append({"id": int(number), "uid": uid})

    current_uids = {entry["uid"] for entry in mailbox}
    unique_uids = len(current_uids) == len(mailbox)
    known = set(known_uids) if unique_uids else set()
    messages = [
        fetch_message(entry["id"], entry["uid"])
        for entry in mailbox
        if entry["uid"] not in known
    ]
    return {"mailbox": mailbox if unique_uids else None, "messages": messages}
