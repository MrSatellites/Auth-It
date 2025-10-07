# 🔐 AuthIt - Proximity-Based Authentication System

AuthIt is a revolutionary two-part authentication system that leverages **Bluetooth Low Energy (BLE)** technology to provide seamless and secure proximity-based authentication. The system consists of an Android mobile application and a Linux PAM module, enabling passwordless authentication when your phone is near your computer.

## 🌟 Key Features

### 🛡️ Security Features
- **SHA-512 Cryptographic Hashing**: Military-grade encryption for secure hash generation
- **Rolling Hash Algorithm**: Dynamic hashes that change every 200ms based on current time and password
- **Proximity-Based Security**: Authentication only works within configurable signal range (RSSI threshold: -87 dBm)
- **Replay Attack Protection**: Time-synchronized rolling hashes prevent replay attacks
- **No Network Dependency**: Works completely offline using local Bluetooth communication

### 📱 Mobile App Features
- **Bluetooth Low Energy Broadcasting**: Efficient, low-power hash transmission
- **Automatic Screen Lock Detection**: Pauses broadcasting when phone is locked for enhanced security
- **Background Operation**: Continuous authentication support with persistent notifications
- **Battery Optimized**: Low-power BLE advertising with intelligent power management
- **Modern Material Design UI**: Clean, intuitive interface with real-time status indicators

### 🖥️ Linux Integration
- **PAM Module Integration**: Seamless integration with Linux authentication system
- **Sudo Authentication**: Replace password prompts with proximity-based authentication
- **Multi-User Support**: Individual user configuration and password management
- **Secure Installation**: Immutable script installation with proper permission handling

## 🏗️ Architecture Overview

```
┌─────────────────┐    BLE Advertisement    ┌─────────────────┐
│  Android App    │◄────────────────────────┤  Linux Client   │
│                 │                         │                 │
│ • Password Hash │    Rolling Hash Data    │ • BLE Scanner   │
│ • BLE Advertiser│                         │ • Hash Validator│
│ • Hash Generator│                         │ • PAM Module    │
└─────────────────┘                         └─────────────────┘
```

### Hash Algorithm Flow
1. **Initial Setup**: User sets password on Android app
2. **Hash Generation**: SHA-512 hash
3. **Rolling Algorithm**: Hash combines: `previous_hash(20) + padding(108) + password_hash(128) + timestamp`
4. **BLE Broadcasting**: First 20 characters broadcast via BLE service data
5. **Linux Prediction**: Client predicts next hash based on received data and local password
6. **Authentication**: Success when predicted hash matches received hash within proximity

## 📦 Installation

### 🖥️ Linux Setup

#### Prerequisites
```bash
# Debian/Ubuntu
sudo apt update
sudo apt install build-essential libpam0g-dev python3 python3-pip bluetooth

# Fedora/RHEL
sudo dnf install gcc pam-devel python3 python3-pip bluez

# Arch Linux  
sudo pacman -S base-devel pam python python-pip bluez
```

#### Installation Steps
1. **Clone the repository**:
   ```bash
   git clone https://github.com/MrSatellites/Auth-It
   cd Auth-It/linux
   ```

2. **Install Python dependencies**:
   ```bash
   pip3 install bleak asyncio
   ```

3. **Build and install the PAM module**:
   ```bash
   sudo make install
   ```

4. **Configure PAM for sudo authentication**:
   ```bash
   # Add this line to the TOP of /etc/pam.d/sudo (before other auth lines)
   auth sufficient pam_authit.so
   ```


### 📱 Android Setup

#### From Releases (Recommended)
1. Download the latest APK from [releases page](https://github.com/MrSatellites/Auth-It/releases)
2. Enable "Install from Unknown Sources" in Android settings
3. Install the APK file

#### Building from Source
```bash
cd Auth-It/android
./gradlew assembleDebug
# APK will be in app/build/outputs/apk/debug/
```

#### App Configuration
1. **Launch AuthIt** on your Android device
2. **Set Password**: Enter the same password you used on Linux setup
3. **Grant Permissions**: Allow Bluetooth and Location access when prompted
4. **Enable Bluetooth**: Ensure Bluetooth is turned on
5. **Start Broadcasting**: Tap "Start Broadcasting" to begin authentication service

## 🎯 Usage Guide

### Basic Authentication Flow
1. **Setup**: Configure both Android app and Linux client with the same password
2. **Authentication**: When prompted for sudo password, bring your phone close to the computer
3. **Success**: Authentication completes automatically when valid hash is detected

### Configuration Options

#### Linux Configuration
- **RSSI Threshold**: Adjust proximity sensitivity in `main.py` (line 12)
- **Scan Timeout**: Modify detection timeout in `main.py` (line 15)
- **Hash Length**: Configure hash broadcast length in `main.py` (line 14)

#### Android Configuration
- **Broadcast Interval**: Modify hash rolling interval in `MainActivity.java` (line 186)
- **TX Power Level**: Adjust transmission power for range control
- **Advertisement Mode**: Change between battery-saving and performance modes

### Security Considerations
- **Password Strength**: Use strong passwords as they form the basis of hash generation
- **Physical Security**: Keep your phone secure as it acts as your authentication token
- **Network Isolation**: System works offline but ensure Bluetooth is only enabled when needed
- **Regular Updates**: Keep both Android app and Linux module updated

## 🔧 Advanced Configuration

### Custom PAM Integration
Add AuthIt to other PAM services beyond sudo:

```bash
# For screen lock (if using custom lock screen)
echo "auth sufficient pam_authit.so" >> /etc/pam.d/lightdm

# For SSH (use with caution)
echo "auth sufficient pam_authit.so" >> /etc/pam.d/sshd
```

### Debugging and Troubleshooting
```bash
# Check PAM module logs
sudo journalctl -f | grep pam_authit

# Test Bluetooth functionality
bluetoothctl scan on

# Check Python dependencies
python3 -c "import bleak; print('BLE support available')"

# Verify installation
ls -la /lib/security/pam_authit.so
ls -la /usr/local/lib/pam_authit/
```

## 🛠️ Development

### Project Structure
```
Auth-It/
├── android/                 # Android Studio project
│   ├── app/src/main/java/  # Java source code
│   ├── app/src/main/res/   # Android resources
│   └── build.gradle        # Build configuration
├── linux/                  # Linux PAM module
│   ├── src/pam_authit.c    # PAM module C code
│   ├── main.py             # Python BLE scanner
│   └── Makefile            # Build system
└── README.md               # This documentation
```

### Building from Source

#### Android Development
```bash
# Requirements: Android Studio, Android SDK
cd android
./gradlew build
./gradlew test
```

#### Linux Development
```bash
# Development build
cd linux
make clean
make all

# Debug build with symbols
make CFLAGS="-g -O0 -fPIC -DDEBUG"
```

### Contributing Guidelines
1. **Fork** the repository and create a feature branch
2. **Test** your changes on multiple Android versions and Linux distributions  
3. **Document** any API changes or new configuration options
4. **Submit** a pull request with clear description of changes

## 🔄 Roadmap

### Version 2.0 (Planned)
- [ ] **Automatic Password Generation**: Cryptographically secure password generation
- [ ] **Improved Android UI**: Material You design with better UX
- [ ] **Multi-Device Support**: Connect multiple phones to one computer
- [ ] **Windows/macOS Support**: Cross-platform PAM equivalent modules

### Version 2.1 (Future)
- [ ] **Dynamic Service UUIDs**: Randomized UUIDs for additional security
- [ ] **Biometric Integration**: Fingerprint/face unlock integration
- [ ] **Remote Revocation**: Ability to remotely disable authentication
- [ ] **Configuration Web Interface**: Browser-based setup and management

## 🤝 Contributing

We welcome contributions! This project was developed in a constrained timeframe, and there are many opportunities for improvement:

- **Security Enhancements**: Code audits, vulnerability assessments
- **Platform Support**: Windows, macOS, iOS implementations  
- **UI/UX Improvements**: Better mobile app interface
- **Documentation**: Additional guides, tutorials, translations
- **Testing**: Unit tests, integration tests, compatibility testing

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature-name`
3. Make your changes and test thoroughly
4. Commit with clear messages: `git commit -m "Add feature description"`
5. Push and create a pull request

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Android BLE API**: Built using Android's comprehensive Bluetooth Low Energy framework
- **PAM Framework**: Leverages Linux's Pluggable Authentication Module system
- **Bleak Library**: Python BLE communication via the excellent Bleak library
- **AllThenticate**: Inspired by proximity-based authentication concepts from [AllThenticate.com](https://www.allthenticate.com/)
- **Material Design**: UI/UX following Google's Material Design guidelines

## 📞 Support

- **Issues**: Report bugs via [GitHub Issues](https://github.com/TuroYT/Auth-It/issues)
- **Discussions**: Join conversations in [GitHub Discussions](https://github.com/TuroYT/Auth-It/discussions)
- **Security**: Report security vulnerabilities privately via GitHub Security tab

---

⭐ **Star this repository if AuthIt helps secure your authentication workflow!** ⭐ 
