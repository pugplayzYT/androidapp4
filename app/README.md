# Puglands

A real-time, geo-location idle game where players claim and own virtual plots of land corresponding to real-world locations, earning passive income in Pug Coins.

## 🐶 The Vibe Check

Puglands is low-key a geo-idle clicker where you flex your entrepreneurial muscle by staking claims in the real world. Think massive multiplayer strategy, but chill. The goal? Be the top dog owner of the map and stack those Pug Coins.

## 🕹️ Key Features (Max Fun, No FOMO)

This app is built to be a main character, so we made sure it has all the cool stuff you wanted:

### 🗺️ Geo-Location Core

* **Real-World Ownership:** Claim and acquire 16m x 16m grid plots based on your actual location using MapLibre GL.
* **Player Range:** Land acquisition is limited by a range, which can be temporarily boosted by 67% using a Range Boost.

### 💸 Economy & Progression (The Save Progress is S-Tier)

* **Persistent Income:** Plots generate passive income in Pug Coins/sec. Your balance and progress are always saved, even when you're offline.
* **Dual Currency:** Manage two economies: **Pugbucks** (primary purchase currency) and **Pug Coins** (premium/income currency).
* **In-Game Purchases & Rewards:** Purchase new plots using Pugbucks, or use Land Vouchers.
* **Redeem for Real Cash:** Pug Coins can be exchanged for real money at a rate of 1 Pug Coin = $0.50.

### 🚀 Boosts & Activities (Lots of Cool Stuff to Do)

* **Income Boost:** Activate a **20x income boost** for 10 minutes to rapidly increase your earnings.
* **Free Land Vouchers:** Earn a free Land Voucher or Boost by watching a rewarded ad, available after a 23-hour cooldown.
* **Coin Exchange:** Directly exchange Pug Coins for Pugbucks at a rate of 1 Pug Coin = 150 Pugbucks.

## 🛠️ Tech Stack (For the Devs)

This is a native Android application built with Kotlin, focusing on a real-time, high-performance experience.

| Component | Library/Technology | Deets |
| :--- | :--- | :--- |
| **Language** | Kotlin | The whole app is Kotlin-first. |
| **Mapping** | MapLibre GL Android SDK | Handles the interactive, stylized world map. |
| **Real-time** | Socket.IO Client | Used for instant updates on user state and land acquisitions. |
| **Networking** | HttpURLConnection / Gson | Standard Android networking and JSON parsing. |
| **Monetization**| Google Mobile Ads SDK | Supports Rewarded Ads for in-game boosts. |
| **Build System**| Gradle | Standard build with Kotlin DSL (`.kts`). |

## ⚙️ Setting Up Locally

To get this project running on your machine, follow these steps. You'll need Android Studio and a connection to the Flask server (specified in `ApiClient.kt`).

### Prerequisites

* Android Studio Jellyfish or newer.
* JDK 21 or higher (as specified in `.idea/misc.xml`).
* The API server running at the base URL defined in `ApiClient.kt` (`https://package-exists-pubs-flowers.trycloudflare.com`).

### Installation

1.  **Clone the Repo:**
    ```bash
    git clone [your-repo-link] puglands
    cd puglands
    ```
2.  **Open in Android Studio:**
    Open the `puglands` directory in Android Studio. Gradle will sync automatically.
3.  **Install Dependencies:**
    The project uses standard dependencies managed by Gradle (see `app/build.gradle.kts`).
4.  **Run the App:**
    Select a physical device or emulator and hit the **Run** button.
5.  **Location Permission:**
    The app requires `ACCESS_FINE_LOCATION` for core gameplay. Grant this permission on the device when prompted.