# 🌍 TravelManager

**TravelManager** to mobilna aplikacja na system Android, zaprojektowana z myślą o osobach planujących i organizujących wspólne podróże.  
Umożliwia tworzenie, edytowanie oraz zarządzanie wycieczkami, planami dnia i współdzieleniem zdjęć z innymi uczestnikami.

📱 **Pobierz aplikację:**  
👉 [TravelManager.apk](https://drive.google.com/file/d/1S-WxJCWIejGsEKRW4S2tb0F9DwKS2fPw/view?usp=sharing)

---

## 🚀 Główne funkcjonalności

- ✈️ **Tworzenie i edycja wycieczek** – dodawanie podstawowych informacji o podróży (nazwa, data, lokalizacja).  
- 🗓️ **Planowanie dni** – możliwość tworzenia planu dnia z opisem i godzinami aktywności.  
- 🧑‍🤝‍🧑 **Udostępnianie zdjęć** – przesyłanie zdjęć i filmów do współdzielonego folderu na Google Drive.  
- 🔐 **Logowanie przez Google** – integracja z Firebase Authentication.  
- ☁️ **Przechowywanie danych** – dane podróży, zdjęć i uczestników zapisywane w Firebase Firestore.  
- 📷 **Galeria multimediów** – przeglądanie zdjęć i filmów z wycieczki.  
- 💾 **Obsługa lokalnych zdjęć** – aplikacja zapisuje lokalne URI zdjęć bez ich kopiowania.

---

## 🛠️ Technologie

| Warstwa | Technologie |
|----------|--------------|
| **Frontend (Android)** | Kotlin, XML, Material Design Components |
| **Backend** | Firebase Firestore, Firebase Authentication, Firebase Storage |
| **Integracje** | Google Sign-In, Google Drive API |
| **Inne** | Glide (ładowanie obrazów), ExoPlayer (odtwarzanie filmów) |

---

## 🔒 Bezpieczeństwo

- Logowanie i autoryzacja przez **Firebase Authentication (OAuth 2.0)**.  
- Ograniczony dostęp do danych Firestore – reguły bezpieczeństwa Firebase chronią dane użytkowników.  
- Brak przechowywania haseł lub danych wrażliwych lokalnie.  
- URI zdjęć są zapisywane w bazie danych bez przechowywania samych plików.


---

## 🧾 Licencja

Projekt stworzony w ramach pracy inżynierskiej.  
Można go wykorzystywać do celów edukacyjnych i prezentacyjnych.
