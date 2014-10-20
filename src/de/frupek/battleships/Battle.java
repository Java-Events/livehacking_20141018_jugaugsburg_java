package de.frupek.battleships;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.tinkerforge.BrickletLCD20x4;
import com.tinkerforge.BrickletMultiTouch;
import com.tinkerforge.BrickletMultiTouch.TouchStateListener;
import com.tinkerforge.IPConnection;
import com.tinkerforge.NotConnectedException;
import com.tinkerforge.TimeoutException;

/**
 * Klasse, die ein einfaches Ratespiel mit Tinkerforgekomponenten.
 * Benötigt wird ein LCD 20x4 Bricklet und ein Multitouch Bricklet.
 * 
 * @author Frupek
 *
 */
public class Battle implements MqttCallback, TouchStateListener {
	private final MqttAsyncClient mqttClient;
	public final BrickletLCD20x4 lcd;
    public final BrickletMultiTouch touch;
    public final IPConnection ipcon;
    
    private Role actualRole;
    private String actualGameTopic;
    private int secretNumber;
	
    private enum Role {
    	INITIATOR,
    	PLAYER
    }
    private static final String HOST = "localhost";
    private static final int PORT = 4223;
    
    /**
     * Konstruktor
     * @param host Host, über den der Masterbrick erreichbar ist
     * @param port Port, über den der Masterbrick erreichbar ist
     * @param brokerUri URI des MQTT Brokers
     */
	public Battle(String host, int port, String brokerUri) throws Exception {
		this.ipcon = new IPConnection();
        this.lcd = new BrickletLCD20x4(Conf.UID_LCD, this.getIpcon());
        this.touch = new BrickletMultiTouch(Conf.UID_TOUCH, this.getIpcon());
        this.getTouch().addTouchStateListener(this);
        
        this.getIpcon().connect(host, port);
        
        this.getLcd().backlightOn();
        this.getLcd().clearDisplay();
        
        this.mqttClient = new MqttAsyncClient(brokerUri, MqttClient.generateClientId());
		
		this.getMqttClient().connect().waitForCompletion();
		this.getMqttClient().setCallback(this);
		this.getMqttClient().subscribe("games/#", 0);
		
		this.writeLine(0, "Zum Starten tippen");
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.paho.client.mqttv3.MqttCallback#connectionLost(java.lang.Throwable)
	 */
	@Override
	public void connectionLost(Throwable arg0) {
		arg0.printStackTrace();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.paho.client.mqttv3.MqttCallback#deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken)
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken arg0) {}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(java.lang.String, org.eclipse.paho.client.mqttv3.MqttMessage)
	 */
	@Override
	public void messageArrived(String arg0, MqttMessage arg1) throws Exception {
		if (this.getActualRole() == null) {
			// Das ist eine neue Challenge
			Integer i = Integer.parseInt(new String(arg1.getPayload()));
			this.setSecretNumber(i);
			this.writeLine(0, "Bitte raten!");
			this.writeLine(1, "Geratene Zahl tippen.");
			this.setActualRole(Role.PLAYER);
			this.setActualGameTopic(arg0);
		}
		if (this.getActualRole().equals(Role.INITIATOR)) {
			// Das ist die Antwort des Spielers
			this.getLcd().clearDisplay();
			this.writeLine(0, new String(arg1.getPayload()));
			
			Thread.sleep(3000);
			
			this.getLcd().clearDisplay();
			this.setActualRole(null);
			this.getMqttClient().subscribe("games/#", 0);
			this.writeLine(0, "Zum Starten tippen");
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.tinkerforge.BrickletMultiTouch.TouchStateListener#touchState(int)
	 */
	@Override
	public void touchState(int state) {
		for (int i = 0; i < 12; i++) {
			if ((state & (1 << i)) == (1 << i)) {
				try {
					if (this.getActualRole() == null) {
						// Neues Spiel initiieren
						this.setActualGameTopic("games/" + System.currentTimeMillis());
						this.getMqttClient().unsubscribe("games/#");
						this.getMqttClient().subscribe(this.actualGameTopic + "/response", 0);
						this.getMqttClient().publish(this.getActualGameTopic(),	("" + i).getBytes(), 0, false);
						this.writeLine(0, "Warte auf Antwort.");
						this.setActualRole(Role.INITIATOR);
					} else if (this.getActualRole().equals(Role.PLAYER)) {
						// Prüfen, ob vorgegebene Zahl erraten wurde
						this.getLcd().clearDisplay();
						if (this.getSecretNumber() == i) {
							this.getMqttClient().publish(this.getActualGameTopic() + "/response", ("Du hast verloren!").getBytes(), 0, false);
							this.writeLine(0, "Du hast gewonnen!");
						} else {
							this.getMqttClient().publish(this.getActualGameTopic() + "/response", ("Du hast gewonnen!").getBytes(), 0, false);
							this.writeLine(0, "Du hast verloren!");
						}
						
						try {
							Thread.sleep(3000);
							
							this.getLcd().clearDisplay();
							this.setActualRole(null);
							this.writeLine(0, "Zum Starten tippen");
						} catch (Exception e) {
							e.printStackTrace();
						} 
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
            }
        }
	}

	/**
	 * Schreibt eine Zeile auf dem LCD Bricklet
	 * @param line Nullbasierter Zeilenindex
	 * @param str Zeileninhalt
	 */
    public void writeLine(int line, String str) {
    	try {
    		// Zeile leeren
    		this.getLcd().writeLine((short)line, (short)0, "                          ");
    		
    		// Neuen Inhalt anzeigen
			this.getLcd().writeLine((short)line, (short)0, str);
		} catch (TimeoutException | NotConnectedException e) {
			e.printStackTrace();
		}
    }
    
    public static void main(String[] args)throws Exception {
    	Battle bat = new Battle(HOST, PORT, Conf.BROKER_URI);
    	
    	System.out.println("Press key to exit"); 
        System.in.read();
        bat.ipcon.disconnect();
    }

	public String getActualGameTopic() {
		return actualGameTopic;
	}

	public void setActualGameTopic(String actualGameTopic) {
		this.actualGameTopic = actualGameTopic;
	}

	private int getSecretNumber() {
		return secretNumber;
	}

	private void setSecretNumber(int secretNumber) {
		this.secretNumber = secretNumber;
	}
	
	private BrickletLCD20x4 getLcd() {
		return lcd;
	}

	private BrickletMultiTouch getTouch() {
		return touch;
	}

	private IPConnection getIpcon() {
		return ipcon;
	}

	private MqttAsyncClient getMqttClient() {
		return mqttClient;
	}
	
	private Role getActualRole() {
		return actualRole;
	}

	private void setActualRole(Role actualRole) {
		this.actualRole = actualRole;
	}
}