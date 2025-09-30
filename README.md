# AuthIt - Authentication Made Easy

AuthIt is an Android application designed to provide seamless and secure authentication using Bluetooth Low Energy (BLE) technology. The app broadcasts secure rolling hashes to authenticate with nearby devices.

## Features

- **Bluetooth Low Energy (BLE) Advertising**: Broadcasts secure rolling hashes for authentication.
- **Secure Hashing**: Uses SHA-512 for generating secure hashes.
- **Dynamic Hash Updates**: Rolling hashes are updated dynamically based on the current time and password.
- **Proximity Detection**: Ensures authentication only within a specific signal range.

## Installation on Linux

1. Clone the repository:
   ```bash
   git clone https://github.com/MrSatellites/Auth-It
   ```
2. Navigate to the project directory:
   ```bash
   cd Auth-It/linux
   ```
3. use 
   ```bash 
   sudo make install
      ``` 
      to install the required dependencies and build the project.

4. Then add "auth sufficient pam_authit.so" to your /etc/pam.d/sudo file to enable authentication using AuthIt.
## Android App Installation

1. Download the APK from the [releases page](https://github.com/MrSatellites/Auth-It/releases).
2. Transfer the APK to your Android device.
3. Install the APK on your device (you may need to enable installation from unknown sources).
4. Open the app and set your desired password.
5. Ensure Bluetooth is enabled on your device.
6. The app will start broadcasting the rolling hash for authentication.
7. Keep the app running in the background for continuous authentication.
8. Make sure to grant any necessary permissions for Bluetooth and location access.

## TODO

1. Automatic Password
2. Improve Android UI
3. Compatibility Windows/MacOs
## Contributing

Contributions are welcome! Please fork the repository and submit a pull request.
Due to a constrained two-day development window, the primary focus was on delivering a functional solution. I look forward to discussing opportunities for code optimization and security improvment.

## License

This project is licensed under the MIT License. See the LICENSE file for details.

## Acknowledgments

- Built using Android's BLE API.
- Inspired by AllThenticate https://www.allthenticate.com/ 
