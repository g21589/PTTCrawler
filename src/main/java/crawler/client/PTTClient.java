package crawler.client;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.PushbackReader;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.net.telnet.TelnetClient;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import crawler.base.Entry;
import crawler.base.Post;
import crawler.base.PostAnalysiser;

public class PTTClient {
	
	private static final Logger log = Logger.getLogger(PTTClient.class);
	private static final boolean isPrintScreen = false;
	private static final boolean isPrintSource = false;
	static {
		log.setLevel(Level.ALL);
	}
	
	public static enum Protocol {
		Telnet, SSH
	}
	
	public static enum Screen {
		MainMenu,	// 主選單 (【主功能表】.*批踢踢實業坊.*呼叫器)
		Board,		// 看板 (文章選讀.*回應.*推文.*轉錄.*相關主題.*找標題/作者.*進板畫面)
		Post,		// 貼文 (瀏覽.*第.*頁.*目前顯示.*第.*行.*離開)
		Unknown
	}
	
	private static final String UserAgent = "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.101 Safari/537.36";
	private static final Pattern VT100ControlPattern = Pattern.compile("\u001B\\[(?<code>[0-9;]*)(?<type>[ABCDHJKmsu])");
	private static final int DEFAULT_TIMEOUT = 10 * 1000;
	
	private static final String MenuHeader = "【主功能表】[\\s\\S]*呼叫器";	
	private static final String BoardFooter = "文章選讀[\\s\\S]*相關主題[\\s\\S]*找標題/作者[\\s\\S]*進板畫面";
	private static final String PostFooter = "瀏覽[\\s\\S]*第[\\s\\S]*頁[\\s\\S]*目前顯示[\\s\\S]*第[\\s\\S]*行[\\s\\S]*離開";
	
	private static final Pattern ENTRYPATTER_PATTERN = Pattern.compile("[●>][ ]*(?<id>[0-9]+|★[ ]+)[ ](?<status>.)(?<karma>[0-9 X]+|爆)(?<date>../..)[ ](?<author>.*?)([\\s□轉]|R:)+");
	private static final Pattern PROGRESS_PATTERN = Pattern.compile("(?<percent>\\d+)%[^\\d]*(?<from>\\d+)~(?<to>\\d+)");

	// 文章代碼(AID): #1L4GI8SM
	private static final Pattern AID_PATTERN = Pattern.compile("文章代碼\\(AID\\):\\s*#(?<aid>........)");
	private static final Pattern URL_PATTERN = Pattern.compile("文章網址:\\s*(?<url>.*?)[\\s\\│]+");
	
	private static final Pattern URL_VERIFY = Pattern.compile("^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
		
	public static final int RETV_TIMEOUT = -1;
	public static final int RETV_EOF = -2;
	public static final int RETV_IOEXCEPTION = -9;
	
	// Screen
	private int posX = -1;
	private int posY = -1;
	private char[][] screen = null;
	
	private Protocol protocol = null;
	private TelnetClient tc = null;
	
	private Channel channel = null;
	private Session session = null;
	
	private InputStream is = null;
	private OutputStream os = null;
	private Thread renderScreenThread = null;
	
	// Matchers
	@SuppressWarnings("unused")
	private String beforeStr = null, matchStr = null, afterStr = null;
	
	public PTTClient() {
		initialize();
	}
	
	/**
	 * 初始化
	 */
	public void initialize() {
		screen = new char[72][80];
		posY = posX = -1;
		clearScreen();
	}
	
	/**
	 * Connect PTT by using protocol
	 * @param protocol
	 * @throws SocketException
	 * @throws IOException
	 * @throws JSchException
	 */
	public void connect(Protocol protocol) throws SocketException, IOException, JSchException {
		
		this.protocol = protocol;
		
		switch (this.protocol) {
		case Telnet:
			log.info("Connect ptt.cc using telnet");
			
			tc = new TelnetClient();
			tc.connect("ptt.cc");
			
			is = tc.getInputStream();	
			os = tc.getOutputStream();
			
			break;
		case SSH:
		default:
			log.info("Connect ptt.cc using SSH (bbs@ptt.cc)");
			
			Properties configuration = new Properties();
			configuration.put("kex", "diffie-hellman-group1-sha1,"
								   + "diffie-hellman-group14-sha1,"
								   + "diffie-hellman-group-exchange-sha1,"
								   + "diffie-hellman-group-exchange-sha256");
			configuration.put("StrictHostKeyChecking", "no");
			
			session = new JSch().getSession("bbsu", "ptt.cc");
			session.setConfig(configuration);
			session.connect(10 * 1000); // Timeout 10 seconds
			channel = (ChannelShell) session.openChannel("shell");
			channel.connect();
			
			is = channel.getInputStream();	
			os = channel.getOutputStream();
			
			break;
		}
		
		renderScreenThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					renderScreen();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		renderScreenThread.setName("Render the screen from InputStream");
		renderScreenThread.setDaemon(true);
		renderScreenThread.start();
		
	}
	
	/**
	 * 關閉連線
	 * @throws IOException
	 */
	public void close() throws IOException {
		
		if (this.protocol == null) {
			return;
		}
		
		switch (this.protocol) {
		case Telnet:
		default:
			if (tc == null) { return; }
			log.info("Close the telnet connection.");
			tc.disconnect();
			break;
		case SSH:
			if (session == null) { return; }
			log.info("Close the SSH connection.");
			channel.disconnect();
			session.disconnect();
			break;
		}
		
	}
	
	/**
	 * 登入PTT
	 * @param username
	 * @param password
	 * @param isDup
	 * @throws Exception
	 */
	public void login(String username, String password, boolean isDup) throws Exception {
		
		if (this.protocol == PTTClient.Protocol.Telnet) {
			send(username + ",\r\n" + password + "\r\n");
		} else if (expect("請輸入代號，或以 guest 參觀，或以 new 註冊:") == 0) {
			send(username + "\r\n" + password + "\r\n");
		} else {
			log.error("Login error.");
		}
		
		int midx = expect("密碼不對", "錯誤", "您想刪除其他重複登入的連線嗎？", "請按任意鍵繼續");
		if (midx < 0) {
			throw new Exception("連線逾時");
		} else if (midx < 2) {
			throw new Exception("密碼不對喔！請檢查帳號及密碼有無輸入錯誤。");
		} else if (midx == 2) {
			send(isDup ? "n\r\n" : "y\r\n");
			if (expect("請按任意鍵繼續") == 0) {
				send("\r\n");
			}
		} else if (midx == 3) {
			send("\r\n");
		}
		
		midx = expect("呼叫器", "您要刪除以上錯誤嘗試的記錄嗎?");
		if (midx == 1) {
			send("Y\r\n");
			midx = expect("呼叫器");
		}
		
		if (midx == 0) {
			log.info("登入成功");
		} else {
			throw new Exception("登入失敗");
		}
		
	}
	
	/**
	 * 登出
	 * @throws IOException
	 */
	public void logout() throws IOException {
		send("qqqqqqeee\nY\n");
	}
	
	/**
	 * 回上一層
	 * @throws IOException
	 */
	public void quit() throws IOException {
		send("q");
	}
	
	/**
	 * 回到主選單
	 * @throws IOException 
	 */
	public void toMainMenu() throws IOException {
		send("qqqqqq");
		if (expect("【主功能表】[\\s\\S]*呼叫器") != 0) {
			log.warn("無法回到【主功能表】");
		} else {
			log.info("已回到【主功能表】");
		}
	}
	
	/**
	 * Go to the board by board name (Current entry maybe 置底文)
	 * @param boardName
	 * @throws Exception 
	 */
	public void toBoard(String boardName) throws Exception {
		toMainMenu();
		send("s" + boardName + "\r\n$$");
		int m1 = expect("看板《" + boardName + "》[\\s\\S]*" + BoardFooter);
		if (m1 != 0) {
			throw new Exception("Fail to go Board.");
		}
	}
	
	/**
	 * Get the popularity of the board 
	 * @param boardName
	 * @return
	 * @throws Exception
	 */
	public int getBoardPopularity(String boardName) throws Exception {
		toBoard(boardName);
		this.refresh();
		if (expect("編號.*日.*期.*作.*者.*文.*章.*標.*題.*人氣:\\d+") == 0) {
			Matcher m = Pattern.compile("\\d+").matcher(matchStr);
			if (m.find()) {
				return Integer.parseInt(m.group());
			}
		}
		return -1;
	}
	
	/**
	 * Get the current screen
	 * @return
	 * @throws IOException 
	 */
	public Screen getCurrentScreen(String boardName) throws IOException {
		refresh(100);
		int matchIndex = expect(
			MenuHeader, 
			"看板《" + boardName + "》[\\s\\S]*" + BoardFooter, 
			PostFooter
		);
		if (matchIndex == 0) {
			return Screen.MainMenu;
		} else if (matchIndex == 1) {
			return Screen.Board;
		} else if (matchIndex == 2) {
			return Screen.Post;
		} else {
			return Screen.Unknown;
		}
	}
	
	/**
	 * setPlainTextMode
	 * @throws IOException 
	 */
	public void setPlainTextMode(String boardName) throws IOException {
		this.refresh();
		send("l\\3q");
		expect("看板《" + boardName + "》[\\s\\S]*" + BoardFooter);
	}
	
	/**
	 * Move to up entry
	 * @return The entry information after moving
	 * @throws Exception
	 */
	public Entry moveUpEntry(String boardName) throws Exception {
		Entry newEntry, oldEntry = getBasicEntryInfo(boardName);
		if (oldEntry.number.equals("1")) {
			throw new Exception("Aready at the toppest entry.");
		}
		send("k");
		int times = 0;
		do {
			Thread.sleep(100);
			newEntry = getBasicEntryInfo(boardName);
			if (++times > 100) {
				throw new Exception("Can not move to the up entry.");
			}
		} while (newEntry.number == oldEntry.number);
		return getFullEntryInfo(boardName);
	}
	
	/**
	 * Move to down entry
	 * @return The entry information after moving
	 * @throws Exception
	 */
	public Entry moveDownEntry(String boardName) throws Exception {
		Entry newEntry, oldEntry = getBasicEntryInfo(boardName);
		send("n");
		int times = 0;
		do {
			Thread.sleep(100);
			newEntry = getBasicEntryInfo(boardName);
			if (++times > 100) {
				throw new Exception("Can not move to the down entry.");
			}
		} while (newEntry.number == oldEntry.number);
		return getFullEntryInfo(boardName);
	}
	
	/**
	 * Go to the latest post entry
	 * @throws Exception
	 */
	public Entry toLatestPost(String boardName) throws Exception {
		send("$$");	// Skip the welcome of the board & to the latest article
		refresh(300);
		if (expect("看板《" + boardName + "》[\\s\\S]*" + BoardFooter) != 0) {
			throw new Exception("Current screen is not \"Board\"");
		}
		
		Entry oldEntry = getFullEntryInfo(boardName), newEntry = null;
		if (!oldEntry.number.equals("★") && !oldEntry.author.equals("-")) {
			return oldEntry;
		}
		for (int times=0; ; times++) {
			
			try {
				
				send("k");
				
				int times2 = 0;
				do {
					Thread.sleep(100);
					newEntry = getFullEntryInfo(boardName);
					if (++times2 > 100) {
						return newEntry;
					}
				} while (oldEntry.id == newEntry.id);
				
				if (!newEntry.number.equals("★") && !newEntry.author.equals("-")) {
					break;
				}
				oldEntry = newEntry;
				
			} catch (Exception e) {
				
			}
			
			if (times >= 100) {
				throw new Exception("Can not go to latest post.");
			}
			
		}
		
		return newEntry;
	}
	
	public Entry getBasicEntryInfo(String boardName) throws Exception {
		if (expect("看板《" + boardName + "》[\\s\\S]*" + BoardFooter) == 0) {
			Matcher matcher = ENTRYPATTER_PATTERN.matcher(matchStr);
			if (matcher.find()) {
				String id = null;
				String url = null;
				String number = matcher.group("id").trim();
				String status = matcher.group("status").trim();
				String karma = matcher.group("karma").trim();
				String date = matcher.group("date").trim();
				String author = matcher.group("author").trim();
				return new Entry(id, number, status, karma, date, author, url);
			} else {
				throw new Exception("Can not match entry. " + matchStr);
			}
		} else {
			throw new Exception("Screen is not \"Board\"");
		}
	}
	
	/**
	 * getFullEntryInfo
	 * @return
	 * @throws Exception 
	 */
	public Entry getFullEntryInfo(String boardName) throws Exception {
		
		boolean isSuccess = false;
		int times = 0;
		Matcher matcher = null;
		
		do {

			expect("看板《" + boardName + "》[\\s\\S]*" + BoardFooter);
			
			matcher = ENTRYPATTER_PATTERN.matcher(matchStr);
			if (matcher.find()) {
				isSuccess = true;
			} else {
				log.warn("Faild to match entry. " + matchStr.replaceAll("\\s+", " "));
				times++;
			}
			
			if (times >= 5) {
				log.error("Faild to match entry. " + matchStr.replaceAll("\\s+", " "));
				throw new Exception("Faild to match entry.");
			}
			
		} while (!isSuccess);
		
		String id = null;
		String url = null;
		String number = matcher.group("id").trim();
		String status = matcher.group("status").trim();
		String karma = matcher.group("karma").trim();
		String date = matcher.group("date").trim();
		String author = matcher.group("author").trim();
		
		if (!author.equals("-")) {
			boolean success = false;
			int count = 0;
			do {
				String[] temp = this.getAID().split("\\t");
				if (temp.length > 0 && !temp[0].equals("")) {
					id = temp[0];
				}
				if (temp.length > 1 && !temp[1].equals("")) {
					url = temp[1];
				}
				if (url != null && URL_VERIFY.matcher(url).find()) {
					success = true;
				}
				count++;
			} while (!success && count < 5);
		}
		
		Entry entry = new Entry(id, number, status, karma, date, author, url);
		return entry;
	}
	
	/**
	 * Get post ID (e.g. 1L4GI8SM)
	 * @return
	 * @throws IOException 
	 */
	public String getAID() throws IOException {
		
		String aid = "";
		String url = "";
		
		send("Q");
		if (expect("請按任意鍵繼續") != 0) {
			return "";
		}
		
		// 文章代碼(AID): #1L4GI8SM
		if (expect(AID_PATTERN) == 0) {
			Matcher m = AID_PATTERN.matcher(matchStr);
			if (m.find()) {
				aid = m.group("aid");
			}
		}
		if (expect(URL_PATTERN) == 0) {
			Matcher m = URL_PATTERN.matcher(matchStr);
			if (m.find()) {
				url = m.group("url");
			}
		}
		
		send("\n");
		
		return aid + "\t" + url;
	}
	
	/**
	 * toPostByNum
	 * @param postNum
	 * @throws IOException 
	 */
	public void toPostByNum(int postNum) throws IOException {
		send(Integer.toString(postNum) + "\r\n");
		send("hq");
		this.refresh();
	}
	
	/**
	 * Go to post by ID
	 * @param postID
	 * @return 
	 * @throws Exception 
	 */
	public Entry toPostByID(String boardName, String postID) throws Exception {
		log.info("Go to AID: #"+ postID);
		send("#" + postID + "\r\nhq");
		this.refresh(200);
		return getFullEntryInfo(boardName);
	}
	
	/**
	 * 下載目前游標位置的貼文
	 * @return
	 * @throws Exception 
	 */
	public String downloadCurrentPost() throws Exception {
		
		StringBuilder content = new StringBuilder();
		int percent = -1;
		int fromLine = -1, toLine = -1;
		int fromLine_bk = -1, toLine_bk = 0;
		
		try {
			
			send("l\f");
			
			while (true) {
				
				int midx = expect(PostFooter, "此頁內容會依閱讀者不同", "此文章無內容[\\s\\S]*按任意鍵繼續");
				if (midx < 0) {
					log.warn("[Skip] Unexpected PostFooter");
					break;
				} else if (midx == 1) {
					log.info("[Skip] 此頁內容會依閱讀者不同");
					break;
				} else if (midx == 2) {
					log.info("Screen: 此文章無內容 [按任意鍵繼續]");
					break;
				}
				
				String[] lines = beforeStr.split("\\n");
				String footer = matchStr;
				
				Matcher matcher = PROGRESS_PATTERN.matcher(footer);
				if (!matcher.find()) {
					throw new Exception("Faild to match footer \"" + footer + "\"");
				}
				fromLine_bk = fromLine;
				
				percent = Integer.parseInt(matcher.group("percent"));
				fromLine = Integer.parseInt(matcher.group("from"));
				toLine = Integer.parseInt(matcher.group("to"));
				if (percent != 100 && fromLine == fromLine_bk) {
					Thread.sleep(50);
					continue;
				}
				
				log.info(String.format("%4d ~ %4d\t%3d%%", fromLine, toLine, percent));
				
				// Append content
				int overlapLines = 0;
				if (fromLine <= toLine_bk) {
					overlapLines = toLine_bk - fromLine + 1;
				}
				for (int i=overlapLines; i<(lines.length-1); i++) {
					content.append(lines[i].trim()).append("\n");
				}
				toLine_bk = toLine;
				
				// Next page or 100% break loop
				if (percent == 100) {
					break;
				} else {
					send((char) 0x06 + "\f");
				}
				
			}
			
			send("q\f");
			
		} catch (IOException e) {
			e.printStackTrace();
			throw new Exception("下載貼文發生錯誤");
		}
		
		return content.toString();
	}
	
	public String getScreen() {
		StringBuilder sb = new StringBuilder(24 * 80);
		for (int i=0; i<24; i++) {
			for (int j=0; j<80; j++) {
				if (screen[i][j] != 0x00) {
					sb.append(screen[i][j]);
				}
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
	public int expect(Object... patterns) {
		return expect(DEFAULT_TIMEOUT, patterns);
	}
	
	public int expect(int timeout, Object... patterns) {
		ArrayList<Pattern> list = new ArrayList<Pattern>();
		for (Object obj : patterns) {
			if (obj instanceof String)
				list.add(Pattern.compile((String) obj));
			else if (obj instanceof Pattern)
				list.add((Pattern) obj);
			else {
				list.add(Pattern.compile(Pattern.quote(obj.toString())));
			}
		}
		return expect(timeout, list);
	}
	
	public int expect(int timeout, List<Pattern> list) {
		
		long endTime = System.currentTimeMillis() + (long) timeout;
		
		while (true) {
			
			String currentScreen = getScreen();
			
			for (int i = 0; i < list.size(); i++) {
				Matcher m = list.get(i).matcher(currentScreen);
				if (m.find()) {
					int matchStart = m.start(), matchEnd = m.end();
					beforeStr = currentScreen.substring(0, matchStart);
					matchStr = m.group();
					afterStr = currentScreen.substring(matchEnd);
					return i;
				}
			}
			
			long waitTime = endTime - System.currentTimeMillis();
			if (waitTime <= 0) {
				return RETV_TIMEOUT;
			}
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
	public void refresh() throws IOException {
		refresh(300);
	}
	
	public void refresh(int waitTime) throws IOException {
		send("\f");
		try {
			Thread.sleep(waitTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @throws KeyManagementException
	 * @throws NoSuchAlgorithmException
	 */
	public static void enableSSLSocket() throws KeyManagementException, NoSuchAlgorithmException {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
        });
 
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new X509TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
 
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }
 
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
    }
	
	/**
	 * Download post by URL (PTT Web Version)
	 * @param url
	 * @return
	 * @throws IOException 
	 */
	public static Post downloadPostByURL(String url) throws Exception {
		return downloadPostByURL(url, DEFAULT_TIMEOUT);
	}
	
	/**
	 * Download post by URL (PTT Web Version)
	 * @param url
	 * @param timeout
	 * @return
	 * @throws Exception
	 */
	public static Post downloadPostByURL(String url, int timeout) throws Exception {
		
		Document doc = Jsoup.connect(url)
							.userAgent(UserAgent)
							.timeout(timeout)
							.cookie("over18", "1")
							.get();
		
		return PostAnalysiser.parsePost(doc, url);
		
	}
	
	/**
	 * Download post by URL with real time update (PTT Web Version)
	 * @param url
	 * @param timeout
	 * @return
	 * @throws Exception
	 */
	public static Post downloadPostByURLwithRU(String url, int timeout) throws Exception {
		
		Document doc = Jsoup.connect(url)
							.userAgent(UserAgent)
							.timeout(timeout)
							.cookie("over18", "1")
							.get();
		
		try {
			
			Element pe = doc.getElementById("article-polling");
			String pollUrl = "http://www.ptt.cc" + pe.attr("data-pollurl");
			String longpollurl = "http://www.ptt.cc" + pe.attr("data-longpollurl");
			
			String longpollJSON = Jsoup.connect(longpollurl)
				 .userAgent(UserAgent)
				 .timeout(5000)
				 .ignoreContentType(true)
				 .cookie("over18", "1")
				 .execute()
				 .body();
			
			JSONObject obj = new JSONObject(longpollJSON);
			String size = obj.get("size").toString();
			String sizeSig = obj.get("sig").toString();
			
			String pollJSON = Jsoup.connect(pollUrl + "&size=" + size + "&size-sig=" + sizeSig)
					 .userAgent(UserAgent)
					 .timeout(3000)
					 .ignoreContentType(true)
					 .cookie("over18", "1")
					 .execute()
					 .body();
			
			obj = new JSONObject(pollJSON);
			String contentHtml = obj.get("contentHtml").toString();
			
			doc.getElementById("main-content").append(contentHtml);
			
		} catch (Exception e) {
		}
		
		return PostAnalysiser.parsePost(doc, url);
		
	}
	
	private void renderScreen() throws IOException {
		
		BufferedReader br = new BufferedReader(
				new InputStreamReader(is, "UTF-8"));
		PushbackReader pr = new PushbackReader(br, 128);
		
		int nc = 0;
		char[] cb = new char[4096];
		
		while ((nc = pr.read(cb)) != -1) {
			
			if (isPrintSource) {
				System.out.print(new String(cb, 0, nc));
			}
			
			for (int pos = 0; pos < nc; pos++) {
				
				char c = cb[pos];
				
				switch (c) {
				case 0x08:	// BS
					if (--posX < 0) {
						--posY;
						posX = 79;
					}
					continue;
				case 0x0A:	// LF
					posY++;
					break;
				case 0x0D:	// CR
					posX = 0;
					break;
				case 0x1B:	// ESC
					int endPos = findEndPosOfVT100Conctrl(cb, nc, pos);
					if (endPos == -1) {
						pr.unread(cb, pos, nc - pos);
						pos = nc;
					} else {
						String ctrlStr = new String(cb, pos, endPos - pos + 1);
						Matcher matcher = VT100ControlPattern.matcher(ctrlStr);
						if (matcher.find()) {
							String code = matcher.group("code");
							String type = matcher.group("type");
							processVT100Conctrl(code, type);
						} else {
							log.error("Unknown VT100 Conctrl");
						}
						pos = endPos;
					}
					break;
				default:
					
					if (c < 0x20 || c == 0x7F) {
						//System.out.printf("ASCII Conctrl: %c(%x)\n----------\n", ch, (int)(ch));
						continue;
					}
					
					if (posX >= 0 && posY >= 0) {
					
						if (isHalfWidth(c)) {
							screen[posY][posX] = c;
						} else {
							screen[posY][posX] = c;
							if (posX < 79) {
								screen[posY][++posX] = 0x00;
							}
						}
						posX++;
						if (posX >= 80) {
							//posY++;
							posX = 79;
						}
					
					}
				}
				
			}
			
			if (isPrintScreen) {
				printScreen();
			}
			
		}
		
		pr.close();
		br.close();
		
	}
	
	private int findEndPosOfVT100Conctrl(char[] cb, int nc, int pos) {
		int endPos = -1;
		for (int i = pos + 1; i < nc; i++) {
			char ec = cb[i];
			if (ec == 'A' || ec == 'B' || ec == 'C' || ec == 'D' || ec == 'H' ||
				ec == 'J' || ec == 'K' || ec == 'm' || ec == 's' || ec == 'u') {
				endPos = i;
				break;
			}
		}
		return endPos;
	}
	
	private void processVT100Conctrl(String code, String type) {
		
		switch (type) {
		case "m":
			break;
		case "H":
			if (code.equals("")) {
				// Cursor Home
				posY = posX = 0;
			} else {
				// Cursor to position
				final Pattern p = Pattern.compile("(?<Y>\\d+);(?<X>\\d+)");
				Matcher m = p.matcher(code);
				if (m.find()) {
					posY = Integer.parseInt(m.group("Y")) - 1;
					posX = Integer.parseInt(m.group("X")) - 1;
				}
			}
			break;
		case "J":
			if (code.equals("2")) {
				// Erases the screen with the background colour and moves the cursor to home.
				clearScreen();
				posY = posX = 0;
			} else if (code.equals("1")) {
				// Erases the screen from the current line up to the top of the screen.
				for (int i=0; i<=posY; i++) {
					Arrays.fill(screen[i], ' ');
				}
			} else if (code.equals("")) {
				// Erases the screen from the current line down to the bottom of the screen.
				for (int i=posY; i<24; i++) {
					Arrays.fill(screen[i], ' ');
				}
			}
			break;
		case "K":
			if (code.equals("")) {
				// Erases from the current cursor position to the end of the current line.
				for (int i=posX; i<80; i++) {
					screen[posY][i] = ' ';
				}
			} else if (code.equals("1")) {
				// Erases from the current cursor position to the start of the current line.
				for (int i=0; i<posX; i++) {
					screen[posY][i] = ' ';
				}
			} else if (code.equals("2")) {
				// Erases the entire current line.
				for (int i=0; i<80; i++) {
					screen[posY][i] = ' ';
				}
			}
			break;
		default:
			// TODO
			log.error("Un implement type: " + type);
		}
		
	}
	
	private void send(String message) throws IOException {
		os.write(message.getBytes());
		os.flush();
	}
	
	private boolean isHalfWidth(char c) {
	    return '\u0000' <= c && c <= '\u00FF'
	        || '\uFF61' <= c && c <= '\uFFDC'
	        || '\uFFE8' <= c && c <= '\uFFEE';
	}
	
	private void printScreen() {
		
		for (int i=0; i<24; i++) {
			for (int j=0; j<80; j++) {
				if (screen[i][j] != 0x00) {
					System.out.print(screen[i][j]);
				}
			}
			System.out.println();
		}
		
	}
	
	private void clearScreen() {
		for (int i=0; i<72; i++) {
			Arrays.fill(screen[i], (char) (' '));
		}
	}
	
	public void crawlAllPostInBoard(String boardname) throws Exception {
		
		new File("Result/" + boardname).mkdirs();
		
		toBoard(boardname);
		Entry entry = toLatestPost(boardname);
		
		for (;;) {
			if (!entry.author.equals("-")) {
				String postContent = downloadCurrentPost();
				log.info(entry.toString());
				PrintWriter pw = new PrintWriter("Result/" + boardname + "/#" + entry.id + ".txt");
				pw.print(postContent);
				pw.close();
			}
			if (entry.number.equals("1")) {
				break;
			}
			entry = moveUpEntry(boardname);
		}
		
	}
	
}
