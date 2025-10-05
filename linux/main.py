import asyncio
import hashlib
import os
import sys
from datetime import datetime, timezone
from typing import Optional

from bleak import BleakScanner
from bleak.backends.device import BLEDevice
from bleak.backends.scanner import AdvertisementData

AUTH_SERVICE_UUID = "fff0" # will be dynamic in future versions
PASSWORD_ENV_FILE = ".env"
CONFIG_DIR = os.path.expanduser("~/.config/authit")
RSSI_THRESHOLD = -87 # low threshold for rssi to consider device "close enough"
HASH_PADDING_LENGTH = 128 
PREDICTION_LENGTH = 20 # Can go up to 26 (because ble size) but i got issues with some android devices
SCAN_TIMEOUT_SECONDS = 10.0


class AuthClient:
    def __init__(self) -> None:
        self.password_hash: str = self._get_password()
        self.last_received_hash: Optional[str] = None
        self.next_prediction: Optional[str] = None
        self.authentication_successful = asyncio.Event()

    def _get_password(self) -> str:
        if not os.path.exists(CONFIG_DIR):
            try:
                os.makedirs(CONFIG_DIR, mode=0o755)
            except OSError as e:
                print(f"[Error] Cannot create config directory: {e}")
                sys.exit(1)
        env_path = os.path.join(CONFIG_DIR, PASSWORD_ENV_FILE)
        if os.path.exists(env_path):
            try:
                with open(env_path, "r") as f:
                    for line in f:
                        if line.startswith("HASH="):
                            password = line.strip().split("=", 1)[1]
                            if not password:
                                try:
                                    os.remove(env_path)
                                except OSError:
                                    pass
                                sys.exit(1)
                            return password
            except (PermissionError, OSError) as e:
                sys.exit(1)

        try:
            password_input = input("Enter your password: ").strip()
        except (EOFError, KeyboardInterrupt):
            sys.exit(1)
            
        if not password_input:
            sys.exit(1)

        hashed_password = hashlib.sha512(password_input.encode()).hexdigest()
        
        try:
            with open(env_path, "w") as f:
                f.write(f"HASH={hashed_password}\n")
            os.chmod(env_path, 0o600)
            print(f"[Info] Password saved to: {env_path}")
        except (PermissionError, OSError) as e:
            print(f"[Error] Cannot write password file: {e}")
            sys.exit(1)

        
        return hashed_password

    def _detection_callback(self, _: BLEDevice, adv: AdvertisementData) -> None:
        if self.authentication_successful.is_set():
            return

        if adv.service_data:
            for uuid, data in adv.service_data.items():
                if AUTH_SERVICE_UUID in str(uuid).lower():
                    self._process_auth_data(data, adv.rssi)

    def _process_auth_data(self, data: bytes, rssi: int) -> None:
        try:
            received_hash = data.decode("utf-8", errors="ignore")
            if received_hash == self.last_received_hash:
                return

            self.last_received_hash = received_hash
            is_authenticated = False

            if rssi >= RSSI_THRESHOLD:
                if self.next_prediction and received_hash == self.next_prediction:
                    print(f"\n[Auth] Received valid hash: {received_hash}")
                    is_authenticated = True

            if is_authenticated:
                self.authentication_successful.set()
            else:
            
                padded_hash = received_hash + "0" * (
                    HASH_PADDING_LENGTH - len(received_hash)
                )
                current_date = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M")
                payload = f"{padded_hash}{self.password_hash}{current_date}".encode()
            
                self.next_prediction = hashlib.sha512(payload).hexdigest()[
                    :PREDICTION_LENGTH
                ]
        except Exception:
            pass

    async def _animate_wait(self) -> None:
        dots = 1
        while not self.authentication_successful.is_set():
            timestamp = datetime.now().strftime("%H:%M:%S")
            status = (
                f"\r[{timestamp}] Waiting for Auth-It device"
                f'{"." * dots}{" " * (3 - dots)}'
            )
            print(status, end="", flush=True)
            dots = (dots % 3) + 1
            try:
                await asyncio.wait_for(
                    self.authentication_successful.wait(), timeout=0.5
                )
            except asyncio.TimeoutError:
                continue

    async def run(self) -> None:
        print(
            """

            ⣎⣱ ⡀⢀ ⣰⡀ ⣇⡀    ⡇ ⣰⡀
            ⠇⠸ ⠣⠼ ⠘⠤ ⠇⠸ ⠉⠉ ⠇ ⠘⠤                                 
"""
        )
        scanner = BleakScanner(
            detection_callback=self._detection_callback, scanning_mode="active"
        )
        await scanner.start()

        animation_task = asyncio.create_task(self._animate_wait())

        try:
            await asyncio.wait_for(
                self.authentication_successful.wait(), timeout=SCAN_TIMEOUT_SECONDS
            )
        finally:
            animation_task.cancel()
            await scanner.stop()


async def main() -> None:
    client = AuthClient()
    try:
        await client.run()
        print("\n[Success] Authentication completed.")
        sys.exit(0)
    except asyncio.TimeoutError:
        print("\n[Error] Timeout: No valid Auth-It devices found.")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())