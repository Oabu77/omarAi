# OMAR AI — Founder Command Center

**OMAR AI** is the sovereign strategic intelligence and executive command system
representing the founder **Omar Mohammad Abunadi**.

It operates as a continuous **Founder Command Center** providing:

- Strategic intelligence and ecosystem oversight
- Infrastructure monitoring and operational reporting
- Financial insight and growth analysis
- Security awareness and risk identification
- Structured advisory across all operating modes

---

## Ecosystem

OMAR AI oversees a unified sovereign network of interconnected platforms:

| Platform | Description |
|---|---|
| **QuranChain™** | Blockchain infrastructure, smart contracts, validator nodes, cross-chain bridges, Zakat automation |
| **Dar Al-Nas™** | Membership governance, halal banking, Takaful insurance, merchant services |
| **DarCloud™** | Cloud identity, email, storage, unified authentication |
| **MeshTalk OS™** | Decentralized telecom, mesh routing, encrypted communications |
| **Halal Card™** | Payment infrastructure and merchant processing |

---

## Getting Started

### Requirements

- Python 3.11+
- An OpenAI API key (optional — the application includes an offline mode)

### Install dependencies

```bash
pip install -r requirements.txt
```

### Configure

```bash
export OPENAI_API_KEY="your-key-here"   # optional — enables full AI responses
export OMAR_AI_MODEL="gpt-4o"           # default model
```

### Run

```bash
python app.py
```

---

## Operating Modes

Switch between modes at the prompt:

```
[OPERATIONS] OMAR AI > switch mode strategy
```

| Mode | Focus |
|---|---|
| `strategy` | Long-term opportunities and ecosystem expansion |
| `operations` | Infrastructure health and service reliability |
| `financial` | Adoption trends and sustainability metrics |
| `security` | Risk detection and anomaly analysis |
| `advisor` | Structured recommendations |

---

## Built-in Commands

```
show ecosystem status         — Overview of all ecosystem components
show infrastructure health    — Node and server health summary
show network performance      — Latency, throughput, uptime metrics
show service adoption metrics — Usage and membership activity
generate operational report   — Full operational status report
generate strategic analysis   — Long-term strategic recommendations
switch mode <mode>            — Change operating mode
help                          — Show command reference
exit / quit                   — Exit the command center
```

---

## Project Structure

```
omarAi/
├── app.py             # Main application — CLI command center
├── config.py          # Configuration (model, modes, ecosystem)
├── system_prompt.md   # OMAR AI system identity and directives
├── requirements.txt   # Python dependencies
├── tests.py           # Unit tests
└── README.md
```

---

## Running Tests

```bash
python -m pytest tests.py -v
# or
python tests.py
```

---

## Security

- API keys are read from environment variables and never hard-coded.
- All sensitive configuration is managed via environment variables.
- The application does not store or transmit conversation history outside
  the current session.
