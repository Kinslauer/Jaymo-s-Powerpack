#!/bin/bash
# Lets the PC reach this Steam Deck over SSH (run once on the Deck: bash deck/enable-ssh.sh from the repo).
# Adds the PC's public key (deck/pc-key.pub) to this account's authorized keys, sets a password if the account has
# none (sudo needs one), switches the SSH service on, and prints this Deck's address on the home network.
set -e
cd "$(dirname "$0")"

mkdir -p ~/.ssh
chmod 700 ~/.ssh
touch ~/.ssh/authorized_keys
if ! grep -qF "$(cat pc-key.pub)" ~/.ssh/authorized_keys; then
	cat pc-key.pub >> ~/.ssh/authorized_keys
	echo "Added the PC's key."
else
	echo "The PC's key was already there."
fi
chmod 600 ~/.ssh/authorized_keys

if ! passwd -S "$USER" 2>/dev/null | grep -q " P "; then
	echo
	echo "This account has no password yet, and turning SSH on needs one. Choose one now (you'll type it twice):"
	passwd
fi

echo
echo "Switching the SSH service on (type your password if asked)..."
sudo systemctl enable --now sshd

echo
echo "Done. Tell Claude on the PC this address:"
ip -4 -o addr show scope global | awk '{print "  " $4}' | cut -d/ -f1
echo "  (user: $USER)"
