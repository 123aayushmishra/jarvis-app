# Jarvis - Voice Assistant App (Phone-Only Build Guide)

PC/laptop ki zaroorat nahi — poora build **GitHub Actions** (cloud) me hoga.
Tumhe sirf phone se code upload karna hai. Neeche step-by-step hai.

## Kya kaam karta hai
- Mic dabao, bolo:
  - **"Call Rahul"** ya natural tarike se **"Rahul ko phone laga do"**
  - **"Message Rahul batao main aa raha hoon"** ya **"Rahul ko bata do late ho jaunga"**
- Simple commands seedha samajh jayega. Thoda ghuma-phira ke bola toh **Claude AI** (tumhari API key se) samajh kar sahi action lega.

---

## PHONE SE SETUP — Step by Step

### Step 1: Termux app install karo
- Play Store pe "Termux" search mat karo (purana version outdated hai) — iske bajaye **F-Droid** se install karo:
  - Browser me jao: https://f-droid.org/en/packages/com.termux/
  - APK download karke install karo (Settings me "install unknown apps" allow karna padega)

### Step 2: Termux me git aur zip tools install karo
Termux kholo aur ye commands ek-ek karke type karo:
```
pkg update -y
pkg install git zip unzip -y
```

### Step 3: Yeh project ka zip Termux me le aao
- Jarvis.zip pehle se hi tumhare phone ke "Downloads" folder me hai (jo maine diya)
- Termux me type karo:
```
termux-setup-storage
cd storage/downloads
```
(Pehli baar "termux-setup-storage" chalane par ek permission popup aayega, Allow karo)
```
unzip JarvisApp.zip -d ~/jarvis
cd ~/jarvis/JarvisApp
```

### Step 4: GitHub account banao (agar nahi hai)
- Browser me github.com pe jao, free account bana lo

### Step 5: Naya empty repository banao
- GitHub website pe "New repository" pe click karo
- Naam do: `jarvis-app`
- **Public** rakho (private bhi chalega but public me Actions free hai easily)
- Koi README/gitignore add mat karna, bilkul empty rakho
- Create karo

### Step 6: GitHub Personal Access Token banao
- GitHub → Settings (apni profile pic pe click) → **Developer Settings** → **Personal Access Tokens** → **Tokens (classic)** → **Generate new token**
- "repo" permission check karo
- Token generate karke **copy kar lo** (yeh dobara nahi dikhega, kahin save kar lo temporarily)

### Step 7: Termux se code push karo
Termux me (abhi bhi `~/jarvis/JarvisApp` folder me ho), ye commands chalao:
```
git init
git add .
git commit -m "first version of jarvis"
git branch -M main
git remote add origin https://github.com/TUMHARA-USERNAME/jarvis-app.git
git push -u origin main
```
Jab username/password maange:
- Username: apna GitHub username
- Password: wahi **token** paste karo jo Step 6 me copy kiya tha (GitHub password nahi chalega)

### Step 8: Build apne aap shuru ho jayega
- GitHub website pe apne repo me jao → **Actions** tab pe click karo
- "Build Jarvis APK" workflow chalta dikhega (2-4 min lagega)
- Green tick ✅ aane ka wait karo

### Step 9: APK download karo
- Usi completed workflow run pe click karo
- Neeche "Artifacts" section me **jarvis-apk** milega, download karo (zip aayega)
- Us zip ke andar `app-debug.apk` hai

### Step 10: APK install karo
- Downloaded zip ko phone ke file manager se extract karo
- `app-debug.apk` pe tap karo → Install
- "Install from unknown source" allow karna padega (Chrome/Files app ke liye)

### Step 11: App kholo aur setup karo
- App kholte hi upar-right corner me gear/settings icon dabao
- Apni **Anthropic API key** paste karo (console.anthropic.com pe jaake, phone browser se hi bana sakte ho — Settings → API Keys → Create Key)
- Mic dabao, permissions allow karo, bolna shuru karo

---

## Agar Code Update Karna Ho Aage
Jab bhi MainActivity.kt ya koi file change karni ho:
1. Termux me file edit karo (`nano` editor use kar sakte ho: `nano app/src/main/java/com/jarvis/assistant/MainActivity.kt`)
2. Phir:
```
git add .
git commit -m "update"
git push
```
3. GitHub Actions apne aap naya APK bana dega — Actions tab se download kar lena

## Important Notes
- Yeh sirf tumhare khud ke phone pe personal use ke liye hai
- API key sirf tumhare phone me (app ke andar) save hoti hai, kahin aur nahi jaati
- Har Claude API call ka thoda sa cost hota hai (paise) tumhari Anthropic account se — bas simple commands ("call X", "message X text") free hain, wo local hi chalte hain
