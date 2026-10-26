#!/usr/bin/env bash
#
# setup-swap.sh — konfiguracja 2 GB swap dla serwera produkcyjnego.
#
# Krytyczne dla maszyny produkcyjnej o ograniczonej pamięci: bez swapu backend
# (Java/Spring) + PostgreSQL laduja OOM. Tworzy /swapfile, ustawia swappiness 10
# i utrwala obie rzeczy na reboot (fstab + sysctl).
#
# Uzycie (raz, przy pierwszym deploy):
#   sudo bash setup-swap.sh
#
# Skrypt jest idempotentny — mozna go bezpiecznie uruchomic ponownie.

set -euo pipefail

SWAPFILE="/swapfile"
SWAP_SIZE="2G"
SWAPPINESS=10
SYSCTL_FILE="/etc/sysctl.d/99-swappiness.conf"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Ten skrypt wymaga uprawnien root. Uruchom: sudo bash setup-swap.sh" >&2
  exit 1
fi

echo "==> Konfiguracja swap (${SWAP_SIZE} @ ${SWAPFILE}, swappiness ${SWAPPINESS})"

# 1. Utworzenie pliku swap (jesli jeszcze nie istnieje)
if [[ -f "${SWAPFILE}" ]]; then
  echo "    ${SWAPFILE} juz istnieje — pomijam tworzenie."
else
  echo "    Tworze ${SWAPFILE} (${SWAP_SIZE})..."
  if ! fallocate -l "${SWAP_SIZE}" "${SWAPFILE}" 2>/dev/null; then
    # fallocate moze zawiesc na niektorych FS (np. ext bez wsparcia) — fallback na dd
    echo "    fallocate niedostepny, uzywam dd..."
    dd if=/dev/zero of="${SWAPFILE}" bs=1M count=2048 status=progress
  fi
  chmod 600 "${SWAPFILE}"
  mkswap "${SWAPFILE}"
fi

# 2. Uprawnienia (na wszelki wypadek, gdyby plik istnial z luznymi prawami)
chmod 600 "${SWAPFILE}"

# 3. Wlaczenie swap (jesli nie jest aktywny)
if swapon --show=NAME --noheadings | grep -qx "${SWAPFILE}"; then
  echo "    Swap juz aktywny."
else
  echo "    Wlaczam swap..."
  swapon "${SWAPFILE}"
fi

# 4. Utrwalenie w /etc/fstab (przetrwa reboot)
if grep -qE "^[[:space:]]*${SWAPFILE}[[:space:]]" /etc/fstab; then
  echo "    Wpis w /etc/fstab juz istnieje."
else
  echo "    Dodaje wpis do /etc/fstab..."
  echo "${SWAPFILE} none swap sw 0 0" >> /etc/fstab
fi

# 5. Swappiness — utrwalone przez sysctl.d + zastosowane od razu
echo "    Ustawiam vm.swappiness=${SWAPPINESS}..."
echo "vm.swappiness=${SWAPPINESS}" > "${SYSCTL_FILE}"
sysctl -w vm.swappiness="${SWAPPINESS}" >/dev/null

echo "==> Gotowe. Aktualny stan:"
swapon --show
echo "    vm.swappiness = $(cat /proc/sys/vm/swappiness)"
free -h
