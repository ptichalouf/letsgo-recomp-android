# Let's Go Recomp does not build natively on Windows. Use WSL2.
#
#   wsl --install -d Ubuntu        (once, then reboot)
#   wsl                            (open Ubuntu)
#   cd /mnt/c/path/to/letsgo-recomp-android  (or better: clone inside the WSL filesystem, ~/letsgo-recomp-android)
#   tools/setup.sh --check
#
# Reporting an issue or a performance result does not need any build tool.
Write-Host "Let's Go Recomp: native Windows is not supported. Run tools/setup.sh inside WSL2 (see comments in this file)."
exit 1
