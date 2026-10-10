# Reeksoptimalisatie met Timefold (lokaal)

Optimaliseert een treinbestuurdersreeks (NMBS, bundel 541) door **per kolom (R1..R7) de cellen van week te laten wisselen**.
Harde regels (H1–H13) worden nooit overtreden; de zachte regels (Z1–Z11) tel je zelf mee met sliders.

## Starten

Op je systeem is enkel podman nodig (`sudo pacman -S podman`). Java, Maven en alle bibliotheken zitten in de container.

    ./run.sh          # eerste keer: image + bibliotheken downloaden (enkele 100 MB), daarna snel
    # open http://localhost:8080  (run.sh probeert dit zelf te openen)

Opruimen: `./clean.sh` verwijdert image, cache en build-map. De pagina is enkel bereikbaar vanaf je eigen laptop (127.0.0.1).

## Gebruik

1. **Reeks**: klik "Open bestand of PDF…" en kies je PDF (wordt in de browser omgezet; pdf.js zit meegeleverd, geen internet nodig),
   of plak je reeks (formaat `week;R1;...;R7`, cel = `300 03:25-12:10` of rustcode). Controleer daarna de kaartjes (diensten,
   gemiddelde duur, CY, RH, ...) met de samenvatting van je PDF.
   `tools/pdf2reeks.py` doet hetzelfde op de commandolijn (in een wegwerpcontainer, zie het bestand).
2. **Sliders** (cijfer 1 tot 10, 0 = telt niet mee): bij **vermijden** betekent een hoog cijfer "dat wil ik zo weinig mogelijk", bij **behouden** "dat wil ik graag hebben".
   Zaterdag en zondag samen zijn één slider (volledige weekends): op 10 zijn halve weekends helemaal uitgesloten. "Twee weekends na elkaar werken" heeft een eigen slider (twee na elkaar telt 1x, drie na elkaar 4x, vier 9x); op 10 is het uitgesloten (harde regel H16).
   Elke regel wordt intern omgerekend naar "hoe vaak komt het voor"; het cijfer bepaalt hoeveel één voorkomen weegt
   (5 = gewoon, elke 2,5 stappen is 5x zwaarder: 3 = ca. 0,3x, 10 = 25x). Zo zijn alle sliders onderling vergelijkbaar.
3. **Start**: kies duur en parallelle runs (standaard aantal cores - 1; elke run is een onafhankelijke zoektocht, de beste wint).
   "Huidig voorstel" als startpunt verfijnt een eerder resultaat.
4. Resultaat: tabel huidig/voorstel, kalender (gestippeld = verplaatst, cijfer = oorspronkelijke week) en de nieuwe reeks als tekst.

## PDF exporteren

Knop "Exporteer als PDF" (bij de kalender of bij de tekst van de nieuwe reeks) opent het afdrukvenster van je browser: kies daar **Opslaan als PDF**
(liggend A4, achtergrondkleuren aan). Pagina 1 = het reeksvoorstel als kleurentabel met uren per week, pagina 2 = rapport met de verbeteringen,
pagina 3 = detail per week, pagina 4 = de nieuwe reeks als tekst.

## Windows-app voor een collega (zonder Docker of Java)

Een Windows-programma moet op Windows gebouwd worden. Daarvoor staat een gratis bouwrobot klaar (GitHub Actions, `.github/workflows/windows.yml`):

1. Maak een gratis account op github.com en een nieuwe (privé) repository. Zet de map erin:

       git init && git add . && git commit -m "reeks" && git branch -M main
       git remote add origin https://github.com/JOUWNAAM/reeks.git && git push -u origin main

2. Op GitHub: tab **Actions** > "Windows-app bouwen" > **Run workflow**. Na enkele minuten staat onderaan de run een bestand **Reeksoptimalisatie-Windows** om te downloaden (zip).
3. Je collega: zip uitpakken (niet vanuit de zip starten) en dubbelklikken op **Reeksoptimalisatie.exe**. Er opent een klein venster en de browser start vanzelf;
   met "Afsluiten" stopt alles. Geen installatie, geen beheerdersrechten, eigen Java zit erin (ongeveer 110 MB).
   Windows kan "onbekende uitgever" tonen (het programma is niet ondertekend): kies **Meer info** > **Toch uitvoeren**.

Zonder GitHub: op een Windows-pc met JDK 21 en Maven kan `bash packaging/build-app.sh` (Git Bash) hetzelfde bouwen in `build/app`.
Dezelfde bouwstappen zijn op Linux getest (app start, rekent en geeft 0 harde overtredingen); de Windows-uitvoering zelf is niet getest.

## Hoe het werkt

* Model: elke dag is een planning entity (`Slot`) met als variabele de cel (`Cell`) die er staat. De waardenbereiken zijn per kolom, dus de reeks blijft altijd een permutatie per kolom.
* Eigen zetten (`ReeksMoves`): twee weken in een kolom wisselen, insertie, hele week wisselen, R6+R7 samen, R1–R5 samen, kolombereik, kolom roteren, twee aansluitende weken wisselen.
* Constraints (`RosterConstraints`): 13 harde (waarvan H15 optioneel) en 12 zachte constraints. Alle regels worden in een cyclische pass (`Evaluator`) berekend; elke zachte constraint krijgt zijn gewicht via `ConstraintWeightOverrides` (de sliders).
* Zoeken: simulated annealing in drie fasen (warm, koeler, koud), elke fase vanaf het beste resultaat.

## Controle

* Op je voorbeeldreeks geeft de checker 0 harde overtredingen, kortste rust 14:00, 133 diensten, gemiddeld 08:03, 4 CY, 15 weekends za+zo rust (11 kwalificerend: zonder CY-weekends).
* `tools/ref_check.py` is een tweede, onafhankelijke implementatie (3x uitgerold i.p.v. modulo). Op 60 willekeurige permutaties gaf die dezelfde tellers als de Java-code.
  Opnieuw draaien: `CLASSPATH=target/classes:target/lib/* python3 tools/ref_check.py`.
* Testresultaat: in 40–120 s op één core daalde de gewogen straf van je reeks sterk (bv. 2856 -> ~1200 met je standaardscores, tot ~880 bij gelijke gewichten), altijd met 0 harde overtredingen. Dat is niet bewezen optimaal; de uitkomst verschilt per run.

## Evaluatie van het scoresysteem

Bevindingen bij het onderzoek naar "Z5 en Z10 op 10 geven toch halve weekends":

* De sliders vermenigvuldigen enkel; ze maken de regels niet vergelijkbaar. Een half weekend kostte 14 x score/5 = 28 punten bij score 10,
  terwijl Z4 (weekuren boven 42 u) op je originele reeks 862 punten telde. De solver ruilt dan gerust 4 halve weekends (112 punten) tegen 600+ punten winst op Z4.
* Oorzaak: Z4 telde dezelfde overbelasting tot 7 keer (week + elk 7-daagse venster). Venstersdeel was 743 van de 862 punten (38 vensters voor 6 weken). Venstercoëfficiënt is nu 0,1 i.p.v. 0,7 (origineel nu 226).
* Z2 (vensters) en Z9 (werkreeksen) en H10 raken dezelfde lange werkreeksen; Z1 en Z3 delen de losse rustdag (Z3 telt die niet opnieuw). Dat is bewust gelaten maar zichtbaar in de tabel.
* Z5 en Z10 zijn geen dubbel: Z5 straft een weekend met één werkdag, Z10 beloont volledig vrije weekends. Een half weekend verliest wel beide (14 + 5).
* Oplossing: sliders genormaliseerd (zie hierboven) en een gedeelde weekendslider waarbij 10 halve weekends uitsluit (harde regel H15, standaard aan). Het kostte in de test weinig: gewogen totaal 1001 met verbod tegenover 1085 zonder (ruwe straf zonder het Z5-deel) en 4 halve weekends.
* De pagina toont nu per slider een prijs per incident ("nu: 28 per half weekend") en per regel het gewogen voorstel en het aandeel in het totaal, zodat je ziet wat werkelijk de uitkomst stuurt.

## Aannames en beperkingen

* Rustcodes: RH/CH en RR/CV tellen als W-type (strengste type); "vrij weekend" sluit CY uit.
* Niet gemodelleerd: 12 u i.p.v. 14 u bij vertraging, kwartaalgrens 520 u, exacte kalendertellers per maand, bevoegdheden/lijnkennis, verlof, feestdagen. H13 is een benadering met de minima uit je originele reeks.
* De zachte formules volgen de tabel uit je masterprompt; waar die ruimte liet (bv. "dagen" in Z1 = aantal dagen tussen twee diensten, Z8-toeslag enkel bij rust < 15 u) is een keuze gemaakt. Controleer de tabel "huidig" met wat je verwacht.
* Timefold is op 1.24.0 vastgepind omdat de eigen zetten interne API gebruiken.
* Meerdere reeksen (3 reeksen Kortrijk met gedeelde diensten) zijn nog niet ingebouwd.
