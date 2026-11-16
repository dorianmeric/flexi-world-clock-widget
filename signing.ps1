# Signing material for the release build.
#
# The keystore itself is intentionally not committed (see .gitignore). Create it once with:
#   keytool -genkeypair -v -keystore keystore/flexi-release.jks -alias flexi \
#           -keyalg RSA -keysize 4096 -validity 10950 -storetype PKCS12 \
#           -storepass flexiwidget -keypass flexiwidget \
#           -dname "CN=Flexi World-Clock Widget, OU=Widgets, O=Flexi, L=Berlin, C=DE"
#
# scripts/build.ps1 reads these values; override any of them with environment variables.
$env:FLEXI_STORE_FILE = 'keystore/flexi-release.jks'
$env:FLEXI_STORE_PASSWORD = 'flexiwidget'
$env:FLEXI_KEY_ALIAS = 'flexi'
$env:FLEXI_KEY_PASSWORD = 'flexiwidget'