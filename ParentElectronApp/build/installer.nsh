; Custom NSIS include for Delta Parent installer
; This adds custom branding and welcome text

; Welcome page customization
!define MUI_WELCOMEPAGE_TITLE "Welcome to ${PRODUCT_NAME} Setup"
!define MUI_WELCOMEPAGE_TEXT "This wizard will guide you through the installation of ${PRODUCT_NAME}.$\r$\n$\r$\nDelta Parent provides advanced parental control and monitoring capabilities for your family's digital safety.$\r$\n$\r$\nClick Next to continue."

; Directory page customization  
!define MUI_DIRECTORYPAGE_TEXT_TOP "Setup will install ${PRODUCT_NAME} in the following folder.$\r$\n$\r$\nTo install in a different folder, click Browse and select another folder."

; Finish page customization
!define MUI_FINISHPAGE_TITLE "Completing the ${PRODUCT_NAME} Setup Wizard"
!define MUI_FINISHPAGE_TEXT "${PRODUCT_NAME} has been successfully installed on your computer.$\r$\n$\r$\nClick Finish to close this wizard."
!define MUI_FINISHPAGE_LINK "Visit our website for support and updates"
!define MUI_FINISHPAGE_LINK_LOCATION "https://deltaparent.com"

; Header image customization
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH
