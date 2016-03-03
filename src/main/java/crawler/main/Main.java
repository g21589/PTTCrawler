package crawler.main;

import org.apache.log4j.PropertyConfigurator;

import crawler.client.PTTClient;
import crawler.client.PTTClient.Protocol;

public class Main {
	
	static {
		PropertyConfigurator.configure("log4j.properties");
	}
	
	public static void main(String[] args) {
		
		PTTClient ptt = new PTTClient();
		
		try {
		
			ptt.connect(Protocol.SSH);
			ptt.login(args[0], args[1], true);
			ptt.crawlAllPostInBoard(args[2]);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
