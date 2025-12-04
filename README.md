# Logistics Optimization with Genetic Algorithm 🚚🧬

Proyek ini adalah aplikasi berbasis Java Spring Boot yang mengimplementasikan Algoritma Genetika (Genetic Algorithm) untuk mengoptimalkan jalur logistik dan pengiriman barang. Aplikasi ini dilengkapi dengan dashboard visual untuk memantau hasil optimasi.

## Fitur Utama

- Genetic Algorithm Engine: Mengoptimalkan rute pengiriman untuk efisiensi jarak dan biaya.
- Web Dashboard: Antarmuka pengguna (Admin & Customer) untuk melihat status dan hasil optimasi.
- Map Visualization: Visualisasi jalur logistik (didukung oleh script generator Python).
- Spring Boot Backend: Arsitektur yang kokoh dan mudah dikembangkan.

## Teknologi yang Digunakan

- Backend: Java (Spring Boot)
- Build Tool: Maven
- Frontend: HTML, CSS (Thymeleaf Templates)
- Scripts: Python (map_generator.py) untuk visualisasi peta tambahan.
- IDE: VS Code / IntelliJ IDEA

## Struktur Proyek

```text
├── src/main/java/com/uas/logistics
│   ├── controller/       # Mengatur request (DashboardController)
│   ├── service/          # Logika inti Algoritma Genetika (GeneticAlgorithmService)
│   └── LogisticsApplication.java
├── src/main/resources/templates/  # Halaman Web (Admin, Home, Customer)
├── scripts/              # Script pendukung (Python)
└── pom.xml               # Dependensi Maven
