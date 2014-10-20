Ein einfaches Ratespiel mit Tinkerforgekomponenten für zwei Mitspieler. Benötigt werden jeweils ein LCD 20x4 und ein Multitouch Bricklet. Zusätzlich muss ein MQTT Message Broker von beiden Clients aus erreichbar sein (etwa Mosquitto, siehe http://www.mosquitto.org).

Ablauf des Spiels:
Beide Spieler sehen zunächst die Aufforderung zur Eingabe einer Nummer über den Touch. Der Mitspieler, der zuerst eine Nummer eingibt (Role.INITIATOR) eröffnet das Spiel. Der andere Mitspieler (Role.PLAYER) darf nun raten, was der Initiator für eine Nummer eingegeben hat. Schließlich wir auf beiden Displays angezeigt, ob das Spiel gewonnen oder verloren wurde. Nach einer Wartezeit von 3 Sekunden kehren beide Bildschirme in den Ausgangszustand zurück und es kann ein neues Spiel gestartet werden.
