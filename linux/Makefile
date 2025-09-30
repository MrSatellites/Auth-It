CC = gcc
CFLAGS = -fPIC -fstack-protector-strong -D_FORTIFY_SOURCE=2 -O2 -w -I.
LDFLAGS = -shared -Wl,-z,relro,-z,now
LDLIBS = -lpam
MODULE_NAME = pam_authit
PYTHON_SCRIPT = main.py
PAM_MODULE_DIR ?= "/lib/security"
PREFIX ?= /usr/local
SCRIPT_INSTALL_DIR = $(PREFIX)/lib/$(MODULE_NAME)

BUILD_DIR = build
SRC_DIR = src
SOURCES = $(SRC_DIR)/$(MODULE_NAME).c
OBJECTS = $(BUILD_DIR)/$(MODULE_NAME).o
TARGET = $(BUILD_DIR)/$(MODULE_NAME).so

.PHONY: all clean install uninstall

all: $(TARGET)

$(TARGET): $(OBJECTS) | $(BUILD_DIR)
	@echo "Linking $(TARGET)"
	@$(CC) $(LDFLAGS) -o $@ $(OBJECTS) $(LDLIBS)

$(BUILD_DIR):
	@mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.c | $(BUILD_DIR)
	@echo "Compiling $<"
	@$(CC) $(CFLAGS) -c $< -o $@

install: all
	# Setup starting :D
	@echo "Creating installation directories..."
	install -d -m 755 "$(DESTDIR)$(PAM_MODULE_DIR)"
	install -d -m 755 "$(DESTDIR)$(SCRIPT_INSTALL_DIR)"

	# Install the PAM module
	@echo "Installing PAM module to $(PAM_MODULE_DIR)/"
	install -m 644 "$(TARGET)" "$(DESTDIR)$(PAM_MODULE_DIR)/"

	# Install the Python script
	@echo "Installing Python script to $(SCRIPT_INSTALL_DIR)/"
	install -m 755 "$(PYTHON_SCRIPT)" "$(DESTDIR)$(SCRIPT_INSTALL_DIR)/"
	@if [ -f .env ]; then \
		echo "Moving .env file to $(SCRIPT_INSTALL_DIR)/"; \
		install -m 644 .env "$(DESTDIR)$(SCRIPT_INSTALL_DIR)/"; \
	fi

	@echo "Setting immutable attribute on installed script..."
	chattr +i "$(DESTDIR)$(SCRIPT_INSTALL_DIR)/$(PYTHON_SCRIPT)"
	
	@echo ""
	@echo "Installation complete."
	@echo "Add 'auth sufficient $(MODULE_NAME).so' to your PAM configuration."
	@echo "Python interpreter: /bin/python3"

uninstall:
	@echo "Uninstalling..."
	@if [ -f "$(DESTDIR)$(SCRIPT_INSTALL_DIR)/$(PYTHON_SCRIPT)" ]; then \
		echo "Removing immutable attribute from script..."; \
		chattr -i "$(DESTDIR)$(SCRIPT_INSTALL_DIR)/$(PYTHON_SCRIPT)"; \
	fi
	@echo "Removing installed files..."
	rm -f "$(DESTDIR)$(PAM_MODULE_DIR)/$(MODULE_NAME).so"
	rm -rf "$(DESTDIR)$(SCRIPT_INSTALL_DIR)"
	@if [ -f "$(DESTDIR)$(SCRIPT_INSTALL_DIR)/.env" ]; then \
		echo "Removing .env file..."; \
		rm -f "$(DESTDIR)$(SCRIPT_INSTALL_DIR)/.env"; \
	fi
	@echo "PAM Auth-It module uninstalled."

clean:
	@echo "Cleaning build directory..."
	@rm -rf $(BUILD_DIR)
