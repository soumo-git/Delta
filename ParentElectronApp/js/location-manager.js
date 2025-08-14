class LocationManager {
    constructor() {
        this.map = null;
        this.marker = null;
        this.isActive = false;
        this.currentLocation = null;
        this.locationWindow = null;
        this.customIcon = null;
        this.init();
    }

    init() {
        // Support both #location-window and direct #location-map
        this.locationWindow = document.getElementById('location-window') || document.getElementById('location-map');
        this.createCustomIcon();
        this.initializeMap();
        this.setupEventListeners();
    }

    createCustomIcon() {
        // Create a custom red pulsing marker
        this.customIcon = L.divIcon({
            className: 'custom-marker',
            html: `
                <div style="
                    width: 20px;
                    height: 20px;
                    background: #dc2626;
                    border: 3px solid #fff;
                    border-radius: 50%;
                    box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.3);
                    animation: pulse 2s infinite;
                    position: relative;
                ">
                    <div style="
                        position: absolute;
                        top: -8px;
                        left: 50%;
                        transform: translateX(-50%);
                        width: 0;
                        height: 0;
                        border-left: 8px solid transparent;
                        border-right: 8px solid transparent;
                        border-bottom: 12px solid #dc2626;
                    "></div>
                </div>
                <style>
                    @keyframes pulse {
                        0% { box-shadow: 0 0 0 0 rgba(220, 38, 38, 0.7); }
                        50% { box-shadow: 0 0 0 10px rgba(220, 38, 38, 0.2); }
                        100% { box-shadow: 0 0 0 20px rgba(220, 38, 38, 0); }
                    }
                </style>
            `,
            iconSize: [20, 20],
            iconAnchor: [10, 10]
        });
    }

    initializeMap() {
        // Remove old map instance if it exists
        if (this.map) {
            this.map.remove();
            this.map = null;
        }
        // Initialize the map with satellite view
        this.map = L.map('location-map', {
            center: [23.5, 87.3], // Default center (India)
            zoom: 13,
            zoomControl: false,
            attributionControl: false
        });

        // Add satellite tile layer for realistic view
        L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
            attribution: '',
            maxZoom: 19
        }).addTo(this.map);

        // Add a hybrid layer for labels over satellite
        L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', {
            attribution: '',
            maxZoom: 19
        }).addTo(this.map);

        // Create initial marker
        this.marker = L.marker([23.5, 87.3], { icon: this.customIcon })
            .addTo(this.map)
            .bindPopup('Waiting for location...', {
                closeButton: false,
                className: 'location-popup'
            });
    }

    setupEventListeners() {
        // Resize map when window is shown
        if (!this.locationWindow) return;
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
                    if (this.locationWindow.classList.contains('active')) {
                        setTimeout(() => {
                            this.map.invalidateSize();
                        }, 100);
                    }
                }
            });
        });
        observer.observe(this.locationWindow, {
            attributes: true,
            attributeFilter: ['class']
        });
    }

    show() {
        this.isActive = true;
        this.locationWindow.classList.add('active');
        setTimeout(() => {
            this.map.invalidateSize();
        }, 300);
    }

    hide() {
        this.isActive = false;
        this.locationWindow.classList.remove('active');
    }

    updateLocation(lat, lng, locationName = null) {
        // Always update location even if not explicitly active since window is permanent
        this.isActive = true;

        const newLatLng = new L.LatLng(lat, lng);
        this.currentLocation = { lat, lng, name: locationName };

        // Update marker position with smooth animation
        this.marker.setLatLng(newLatLng);
        
        // Pan map to new location smoothly
        this.map.panTo(newLatLng, {
            animate: true,
            duration: 1.5,
            easeLinearity: 0.1
        });

        // Update popup content
        let popupContent = `<div style="color: white; font-size: 11px; text-align: center;">`;
        if (locationName) {
            popupContent += `<strong>${locationName}</strong><br>`;
        }
        popupContent += `${lat.toFixed(6)}, ${lng.toFixed(6)}</div>`;
        
        this.marker.getPopup().setContent(popupContent);
        this.marker.openPopup();

        // Update UI elements
        this.updateLocationInfo(lat, lng, locationName);

        // Add a subtle notification effect
        this.locationWindow.style.boxShadow = '0 8px 32px rgba(220, 38, 38, 0.4)';
        setTimeout(() => {
            this.locationWindow.style.boxShadow = '0 8px 32px rgba(0, 0, 0, 0.8)';
        }, 1000);
    }

    updateLocationInfo(lat, lng, locationName) {
        const locationNameEl = document.getElementById('location-name');
        const locationCoordsEl = document.getElementById('location-coords');
        
        if (locationNameEl) {
            locationNameEl.textContent = locationName || 'Unknown Location';
        }
        
        if (locationCoordsEl) {
            locationCoordsEl.textContent = `Coordinates: ${lat.toFixed(6)}, ${lng.toFixed(6)}`;
        }
    }

    // Method to reverse geocode coordinates to get location name
    async reverseGeocode(lat, lng) {
        try {
            const response = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`);
            const data = await response.json();
            
            if (data && data.display_name) {
                // Extract meaningful location name
                const address = data.address;
                let locationName = '';
                
                if (address.railway) {
                    locationName = `${address.railway} Railway Station`;
                } else if (address.amenity) {
                    locationName = address.amenity;
                } else if (address.shop) {
                    locationName = address.shop;
                } else if (address.building) {
                    locationName = address.building;
                } else if (address.road) {
                    locationName = address.road;
                    if (address.suburb) locationName += `, ${address.suburb}`;
                } else if (address.suburb) {
                    locationName = address.suburb;
                } else if (address.city || address.town || address.village) {
                    locationName = address.city || address.town || address.village;
                }
                
                return locationName || data.display_name.split(',')[0];
            }
        } catch (error) {
            console.error('Reverse geocoding failed:', error);
        }
        return null;
    }
}

// Global function to toggle location window
window.toggleLocationWindow = function(show) {
    if (window.locationManager) {
        if (show) {
            window.locationManager.show();
        } else {
            window.locationManager.hide();
        }
    }
};

// Test function to show location window with demo data
window.testLocationWindow = function() {
    console.log('Testing location window...');
    if (window.locationManager) {
        window.locationManager.show();
        // Add demo location after a short delay
        setTimeout(() => {
            window.locationManager.updateLocation(23.5, 87.3, 'Test Location - Rampurhat Railway Station');
        }, 500);
    }
};

// Initialize global location manager
window.LocationManager = LocationManager;

