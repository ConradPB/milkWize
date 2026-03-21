# 🥛 MilkWize

**MilkWize** is a professional-grade dairy management ecosystem built to solve the unique connectivity and data challenges of East African agriculture. It empowers farmers and cooperatives to transform raw milk data into actionable financial and biological insights.

---

## 🌟 Overview

In regions where high temperatures can drop milk production by 15–20% in a single day, data isn't just a luxury—it's a survival tool. **MilkWize** bridges the gap between traditional farming and modern AgTech by providing a resilient, offline-first platform for tracking production, monitoring herd health, and managing payments.

---

## 🚀 Key Features

### 📡 Offline-First & Real-Time Sync

- **Resilient Data Entry:** Designed for the field. Farmers can log yields in areas with zero connectivity using local SQLite persistence.
- **Supabase Integration:** Automatic background synchronization ensures data is backed up to the cloud the moment a 3G/4G signal is detected.
- **Persistent Sessions:** Secure local authentication allows farmers to stay logged in for weeks without needing an internet "handshake."

### 🌡️ Weather-Aware Analytics

- **Heat Stress Monitoring:** Real-time integration with Open-Meteo/OpenWeatherMap APIs.
- **Biological Context:** The app calculates the **Temperature-Humidity Index (THI)** to alert farmers when environmental factors are impacting milk yield.

### 📊 Audit-Ready Reporting

- **Secure CSV Export:** Implementation of Android **FileProvider** for generating and sharing encrypted production reports.
- **Instant Sharing:** One-tap export to WhatsApp, Gmail, or Telegram for cooperative auditing and bank loan applications.

---

## 🛠 Tech Stack

| Layer              | Technology                                       |
| :----------------- | :----------------------------------------------- |
| **UI/UX**          | Kotlin + Jetpack Compose (Material 3)            |
| **Backend**        | Supabase (Postgres & Auth)                       |
| **Local Database** | Room / SQLite (Offline Persistence)              |
| **Networking**     | Retrofit + OkHttp (Weather API)                  |
| **Architecture**   | MVVM (Model-View-ViewModel) + Clean Architecture |

---

## 📊 Business Logic: The "Why"

MilkWize uses the **Temperature-Humidity Index (THI)** to provide context to yield drops:

$$THI = (1.8 \times T + 32) - [(0.55 - 0.0055 \times RH) \times (1.8 \times T - 26)]$$

_Where $T$ = Temperature (°C) and $RH$ = Relative Humidity (%)_

By correlating this data with daily production, the app identifies "Heat Stress" days, allowing for better herd management and resource allocation.

---

## 👨‍💻 Project Context

This project was developed by a developer with a background in International Relations, Economics, and Agribusiness ownership. It represents a synthesis of technical software engineering and deep domain knowledge of the Ugandan dairy sector.

---

## 📝 License

This project is for demonstration and professional portfolio purposes. All rights reserved.
