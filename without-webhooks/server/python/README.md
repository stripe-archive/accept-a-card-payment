# Accepting a card payment

## Requirements

- Python 3

## How to run

1. Create and activate a new virtual environment

**MacOS / Unix**

```
python3 -m venv env
source env/bin/activate
```

**Windows (PowerShell)**

```
python3 -m venv env
.\env\Scripts\activate.bat
```

2. Install dependencies

```
pip install -r requirements.txt
```

3. Configure .env

**MacOS / Unix**

```
cp dev.env .env
nano .env # Set its keys
```

**Windows (PowerShell)**

```
Copy-Item ".\dev.env" -Destination ".\.env"
# Set its keys in .env with any code editor
```

4. Export and run the application

**MacOS / Unix**

```
export FLASK_APP=server.py
python3 -m flask run --port=4242
```

**Windows (PowerShell)**

```
$env:FLASK_APP=“server.py"
python3 -m flask run --port=4242
```

5. Go to `localhost:4242` in your browser to see the demo
