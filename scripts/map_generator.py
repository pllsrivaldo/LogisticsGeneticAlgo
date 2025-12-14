import folium
from geopy.distance import geodesic
import os
import sys

def generate_map():
    current_dir = os.getcwd()
    
    # Target 1: Folder Source Code (agar file tetap ada saat coding)
    path_src = os.path.join(current_dir, "src", "main", "resources", "static", "map.html")
    
    # Target 2: Folder Target/Build (agar web server bisa baca langsung)
    path_target = os.path.join(current_dir, "target", "classes", "static", "map.html")
    
    # Pastikan folder target ada (kadang folder static belum ke-create di target)
    os.makedirs(os.path.dirname(path_target), exist_ok=True)

    print(f"Generating map...")
    
    # DATA & VISUALISASI 
    cities = {
        'Jakarta': (-6.1751, 106.8272),
        'Surabaya': (-7.2575, 112.7521),
        'Semarang': (-6.9667, 110.4381),
        'Balikpapan': (-1.2558, 116.8253),
        'Makassar': (-5.1477, 119.4327),
        'Batam': (1.1445, 104.0305),
        'Belawan': (3.7844, 98.6804)
    }

    # Koneksi Rute Sederhana
    connections = [
        ('Batam', 'Jakarta'), ('Batam', 'Belawan'), ('Batam', 'Balikpapan'),
        ('Balikpapan', 'Makassar'), ('Makassar', 'Surabaya'), ('Surabaya', 'Semarang'),
        ('Jakarta', 'Semarang'), ('Jakarta', 'Balikpapan'), ('Surabaya', 'Balikpapan')
    ]

    map_center = [-2.5, 118.0]
    m = folium.Map(location=map_center, zoom_start=5, tiles="CartoDB positron")

    for city, coords in cities.items():
        folium.CircleMarker(
            location=coords, radius=6, color='blue', fill=True, fill_color='cyan',
            fill_opacity=0.8, popup=f"<b>{city}</b>"
        ).add_to(m)

    for start_city, end_city in connections:
        if start_city in cities and end_city in cities:
            start_coords = cities[start_city]
            end_coords = cities[end_city]
            dist = geodesic(start_coords, end_coords).km
            
            folium.PolyLine([start_coords, end_coords], color='red', weight=2, opacity=0.7).add_to(m)
            folium.PolyLine(
                [start_coords, end_coords], color='transparent', weight=15, 
                tooltip=f"{start_city}-{end_city}: {dist:.0f} KM"
            ).add_to(m)

    # SIMPAN KE DUA LOKASI 
    m.save(path_src)    # Simpan ke src
    m.save(path_target) # Simpan ke target (Live)
    
    print(f"Map saved successfully to: {path_target}")

if __name__ == "__main__":
    generate_map()