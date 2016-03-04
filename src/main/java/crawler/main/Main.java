package crawler.main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import crawler.base.Entry;
import crawler.client.PTTClient;
import crawler.client.PTTClient.Protocol;

public class Main {
	
	private static final Logger log = Logger.getLogger(Main.class);
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	
	public static String username = null;
	public static String password = null;
	public static String boardname = null;
	public static boolean isMultiThread = false;
	
	static {
		PropertyConfigurator.configure("log4j.properties");
	}
	
	/** 
	 * 解析輸入參數
	 * @param args 輸入的參數
	 */
	private static void parseArgs(String[] args) {
		
		isMultiThread = false;
		
		for (int i=0; i<args.length; i++) {
			
			switch (args[i].charAt(0)) {
			case '-':
				if (i+1 < args.length) {
					if (args[i].equals("-u") || args[i].equals("-username")) {
						username = args[++i];
					} else if (args[i].equals("-p") || args[i].equals("-password")) {
						password = args[++i];
					} else if (args[i].equals("-b") || args[i].equals("-board")) {
						boardname = args[++i];
					} else {
						throw new IllegalArgumentException("Not a valid argument: " + args[i]);
					}
				} else if (args[i].equals("-m")) {
					isMultiThread = true;
				} else {
					throw new IllegalArgumentException("No config value after " + args[i]);
				}
				break;
			default:
				throw new IllegalArgumentException("Not a valid argument: " + args[i]);
			}
			
		}
		
		if (username == null) {
			throw new IllegalArgumentException("Require username. Please use -u [username] in arguments.");
		}
		if (password == null) {
			throw new IllegalArgumentException("Require password. Please use -p [password] in arguments.");
		}
		if (boardname == null) {
			throw new IllegalArgumentException("Require boardname. Please use -b [boardname] in arguments.");
		}
		
	}
	
	/**
	 * 抓取看板內之所有文章
	 * @throws Exception
	 */
	public static void crawlAllPosts() {
		
		final String savePath = "Results/" + boardname + "_" + sdf.format(new Date());
		new File(savePath).mkdirs();
		PTTClient ptt = new PTTClient();
		
		try {
			
			ptt.connect(Protocol.Telnet);
			ptt.login(username, password, false);
			ptt.toBoard(boardname);
			Entry entry = ptt.toLatestPost(boardname);
			
			for (;;) {
				if (!entry.author.equals("-")) {
					String postContent = ptt.downloadCurrentPost();
					log.info(entry.toString());
					PrintWriter pw = new PrintWriter(savePath + "/#" + entry.id + ".txt");
					pw.print(postContent);
					pw.close();
				}
				if (entry.number.equals("1")) {
					break;
				}
				entry = ptt.moveUpEntry(boardname);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				ptt.logout();
				ptt.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * 抓取看板內之所有文章(多執行序版本)
	 */
	public static void crawlAllPostsMultiThread() {
		
		// Step1. Get the latest entry number
		PTTClient ptt = new PTTClient();
		int latestEntryNumber = 1;
		
		try {
			ptt.connect(Protocol.SSH);
			ptt.login(username, password, false);
			ptt.toBoard(boardname);
			Entry entry = ptt.toLatestPost(boardname);
			latestEntryNumber = Integer.parseInt(entry.number);
			log.info("共" + latestEntryNumber + "則貼文");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				ptt.close();
				Thread.sleep(10 * 1000);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		// Step2. Multi-thread crawl
		final int ThreadPoolSize = 3;
		ExecutorService executor = Executors.newFixedThreadPool(ThreadPoolSize);
		
		int partSize = latestEntryNumber / 10;
		for (int i=0; i<11; i++) {
			int from = i * partSize + 1;
			int to = from + partSize - 1;
			if (from <= latestEntryNumber && to <= latestEntryNumber) {
				executor.execute(() -> crawlPostsByRange(from, to));
			}
		}
		
		try {
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * 抓取看板內指定範圍的文章
	 * @param fromNum
	 * @param toNum
	 * @throws Exception
	 */
	public static void crawlPostsByRange(int fromNum, int toNum) {
		
		final String savePath = "Results/" + boardname + "_" + sdf.format(new Date());
		new File(savePath).mkdirs();
		PTTClient ptt = new PTTClient();
		
		try {
		
			ptt.connect(Protocol.SSH);
			ptt.login(username, password, true);
			ptt.toBoard(boardname);
			Entry entry = ptt.toEntryByNum(boardname, fromNum);
		
			for (;;) {
				if (!entry.author.equals("-")) {
					String postContent = ptt.downloadCurrentPost();
					log.info(entry.toString());
					PrintWriter pw = new PrintWriter(savePath + "/#" + entry.id + ".txt");
					pw.print(postContent);
					pw.close();
				}
				if (entry.number.equals(Integer.toString(toNum)) || entry.number.equals("★")) {
					break;
				}
				entry = ptt.moveDownEntry(boardname);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				ptt.logout();
				ptt.close();
				Thread.sleep(10 * 1000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public static void main(String[] args) {
		
		parseArgs(args);
		
		if (isMultiThread) {
			crawlAllPostsMultiThread();
		} else {
			crawlAllPosts();
		}
		
	}

}
