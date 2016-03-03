package crawler.base;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import crawler.base.Reply.ReplyType;

public class PostAnalysiser {
	
	static final Logger log = Logger.getLogger(PostAnalysiser.class);
	
	static {
		log.setLevel(Level.INFO);
	}
	
	private static final Pattern PostHeaderPattern = Pattern.compile(
		"作者:*\\s+(?<author>.*?)\\s+((看板|站內):*\\s+(?<board>.*?))*" +
		"\\s*標題:*\\s+(?<title>.*?)[\\r\\n]+" + 
		"\\s*時間:*\\s+(?<time>.*?)[\\r\\n]+"+ 
		"[-─]*\\s*"
	);
	
	private static final Pattern PostFooterPattern = Pattern.compile(
		"(" +
			"[-]*\\s+※\\s+發信站:.*來自:\\s+(?<ip>[\\d\\.]+)\\s+" +
			"※\\s+文章網址:\\s+(?<url>.*?)[\\r\\n]+" +
		")|(" +
			"[-]*\\s+※\\s+發信站.*?\\s+"+
			"◆\\s+From:\\s+(?<ip2>[\\d\\.]+)[\\r\\n]+" +
		")|(" +
			"[-]*\\s+※\\s+發信站:.*(來自:\\s+(?<ip3>[\\d\\.]+))*\\s+" +
		")|(" +
			"[-]*※\\s+文章網址:\\s+(?<url2>.*?)[\\r\\n]+" +
		//")|(" +
		//	"\\s+[-]+\\s+" +
		")"
	);

	private static final Pattern PostReplyPattern = Pattern.compile(
		"(?<type>[→推噓])\\s*(?<author>.*?):\\s*(?<content>.*?)[\\r\\n]+"
	);
	
	private static final Pattern ReplyDatePattern = Pattern.compile(
		"\\d+/\\d+[ ]\\d+:\\d+"
	);
	
	private static final Pattern URLTimePattern = Pattern.compile(
		"M\\.(?<timestamp>\\d+)\\."
	);
	
	// Example: Sun Mar 22 23:43:00 2015
	private static final SimpleDateFormat postSDF = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);
	private static final SimpleDateFormat postSDF2 = new SimpleDateFormat("EEE MMMd HH:mm:ss yyyy", Locale.ENGLISH);
	private static final SimpleDateFormat replySDF = new SimpleDateFormat("yyyy/MM/dd HH:mm");
	
	public static Post parsePost(Entry entry, String rawText) {
		
		Post post = new Post();
		
		String content = rawText;
		
		post.setID(entry.id);
		post.setAuthor(entry.author);
		post.setUrl(entry.url);
		
		boolean contentFlag = false;
		
		try {
			
			Matcher matcher = PostHeaderPattern.matcher(rawText);
			if (!matcher.find()) {
				//log.info(rawText);
				throw new Exception("The post not match \"PostHeaderPattern\" format.");
			}
			
			post.setTitle(matcher.group("title").trim());
			
			String timeStr = matcher.group("time").trim();
			Date postTime = null;
			try {
				postTime = postSDF.parse(timeStr);
			} catch (ParseException e) {
				try {
					postTime = postSDF2.parse(timeStr);
				} catch (ParseException e2) {
					 
				}
			}
			
			Calendar cal = Calendar.getInstance();
		    cal.setTime(postTime);
			int year = cal.get(Calendar.YEAR);
			
			post.setPostTime(postTime);
			
			String remainText = rawText.substring(matcher.end());
			content = remainText;
			
			matcher = PostFooterPattern.matcher(remainText);
			if (matcher.find()) {
				if (matcher.group(1) != null) {
					//System.out.println(matcher.group("ip").trim());
					//System.out.println(matcher.group("url").trim());
					post.setUrl(matcher.group("url").trim());
				//} else if (matcher.group(4) != null) {
					//System.out.println(matcher.group("ip2").trim());
				//} else if (matcher.group(6) != null) {
					//System.out.println(matcher.group("ip3").trim());
				} else if (matcher.group(9) != null) {
					post.setUrl(matcher.group("url2").trim());
				}
				
				//System.out.println();
				contentFlag = true;
				content = remainText.substring(0, matcher.start()).trim();
				remainText = rawText.substring(matcher.end());
				post.setContent(content);
			} else {
				//throw new Exception("The post not match \"PostFooterPattern\" format.");
				post.setContent(content);
			}
			
			matcher = PostReplyPattern.matcher(remainText);
			int upVoteCount = 0, downVoteCount = 0, neutralCount = 0;
			while (matcher.find()) {
				
				if (!contentFlag) {
					contentFlag = true;
					post.setContent(remainText.substring(0, matcher.start()));
				}
				
				//DBObject docReply = new BasicDBObject();
				Reply reply = new Reply();
				
				String type = matcher.group("type");
				String author = matcher.group("author");
				String temp = matcher.group("content");
				String replyContent = temp;
				Date ReplyDate = null;
				
				ReplyType rt = null;
				if (type.equals("推")) {
					upVoteCount++;
					rt = ReplyType.Positive;
				} else if (type.equals("噓")) {
					downVoteCount++;
					rt = ReplyType.Negative;
				} else if (type.equals("→")) {
					neutralCount++;
					rt = ReplyType.Normal;
				}
				
				if (temp.length() > 11) {	// Time pattern is 11 characters (MM/DD HH:mm)
					
					Matcher m = ReplyDatePattern.matcher(temp);
					if (m.find()) {
						
						replyContent = temp.substring(0, m.start());
						String rdstr = m.group();
						try {
							rdstr = year + "/" + rdstr;
							ReplyDate = replySDF.parse(rdstr);
						} catch (ParseException e) {
							log.warn("Fail to parae reply date. (\"" + temp + "\")");
						}
					
					}
				}
				
				reply.setID(author);
				reply.setType(rt);
				reply.setContent(replyContent);
				reply.setPostTime(ReplyDate);
				
				post.addReply(reply);
				
			}
			
			post.setUpVoteCount(upVoteCount);
			post.setDownVoteCount(downVoteCount);
			post.setNeutralCount(neutralCount);
			
			//post.setParsed(true);	
			
		} catch (Exception e) {
			
			log.warn("Fail to parse the post. (PostID: " + entry.id + ") " + e.toString());
			//e.printStackTrace();
			
			post.setContent(content);
			//post.setParsed(false);
			
		}
		
		return post;
		
	}
	
	public static Post parsePost(Document doc, String url) {
		
		Post post = new Post();
		
		post.setUrl(url);
		
		Element mainContent = doc.getElementById("main-content");
		
		// 標頭
		int year = 2015;
		Elements metas = mainContent.select(".article-metaline, .article-metaline-right");
		for (Element meta : metas) {
			String tag = meta.select(".article-meta-tag").text();
			String value = meta.select(".article-meta-value").text();
			if (tag.equals("作者")) {
				Matcher matcher = Pattern.compile("(?<author>.*?)\\s+").matcher(value);
				if (matcher.find()) {
					value = matcher.group();
				}
				post.setAuthor(value.trim());
			} else if (tag.equals("標題")) {
				post.setTitle(value.trim());
			} else if (tag.equals("時間")) {
				Date postTime = null;
				try {
					postTime = getTimeFromPttURL(url);
					postTime = postSDF.parse(value.trim());
					Calendar cal = Calendar.getInstance();
				    cal.setTime(postTime);
				    year = cal.get(Calendar.YEAR);
					if (year <= 1980) {
						postTime = getTimeFromPttURL(url);
					}
				} catch (Exception e) {
					log.info("Cannot parse the post time.");
				} finally {
					post.setPostTime(postTime);
				}
			} else if (tag.equals("看板")) {
				
			} else {
				
			}
			
			meta.remove();
		}
		
		if (post.getPostTime() == null) {
			try { post.setPostTime(getTimeFromPttURL(url)); } catch (Exception e) {}
		}
		
		// 推文
		Elements pushs = mainContent.select("div.push");
		int upVoteCount = 0, downVoteCount = 0, neutralCount = 0;
		for (Element push : pushs) {
			
			Reply reply = new Reply();
			
			String pushTag = push.select(".push-tag").text().trim();
			String pushUserID = push.select(".push-userid").text().trim();
			String pushContent = push.select(".push-content").text().replaceFirst("^:\\s+", "").trim();
			String pushDatetime = push.select(".push-ipdatetime").text().trim();
			
			ReplyType rt = null;
			if (pushTag.equals("推")) {
				upVoteCount++;
				rt = ReplyType.Positive;
			} else if (pushTag.equals("噓")) {
				downVoteCount++;
				rt = ReplyType.Negative;
			} else if (pushTag.equals("→")) {
				neutralCount++;
				rt = ReplyType.Normal;
			}
			
			reply.setID(pushUserID);
			reply.setType(rt);
			reply.setContent(pushContent);
			try {
				pushDatetime = pushDatetime.replaceAll("\\d+(\\.\\d+)+", "").trim();
				reply.setPostTime(replySDF.parse(year + "/" + pushDatetime));
			} catch (Exception e) {
				log.warn(e.getMessage());
			}
			
			post.addReply(reply);
			
			push.remove();
		}
		
		// 其他(發信站, 文章網址, ...)
		Elements f2s = mainContent.select("span.f2");
		Pattern F2Pattern = Pattern.compile("※\\s+(?<type>發信站|文章網址|轉錄者|編輯):\\s+((?<id>\\w+)\\s+)*");
		for (Element f2 : f2s) {
			String text = f2.text().trim();
			Matcher m = F2Pattern.matcher(text);
			if (m.find()) {
				if (m.group("type").equals("編輯")) {
					if (post.getAuthor() == null) {
						post.setAuthor(m.group("id")); 
					}
				} else if (m.group("type").equals("轉錄者")) {
					if (post.getAuthor() == null) {
						post.setAuthor(m.group("id")); 
					}
				}
				f2.remove();
			}
		}

		String content = mainContent.wrap("<pre>").text();
		content = content.replaceAll(PostFooterPattern.pattern(), "")
						 .replaceAll("※\\s+.*轉錄至看板.*(\\d+:\\d+)*", "")
						 .trim();
		
		post.setUpVoteCount(upVoteCount);
		post.setDownVoteCount(downVoteCount);
		post.setNeutralCount(neutralCount);
		post.setContent(content);
		
		return post;
		
	}
	
	/**
	 * br2nl
	 * @param html
	 * @return
	 */
	public static String br2nl(String html) {
	    if(html == null) {
	        return html;
	    }
	    Document document = Jsoup.parse(html);
	    document.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
	    document.select("br").append("\\n");
	    document.select("p").prepend("\\n\\n");
	    String s = document.html().replaceAll("\\\\n", "\n");
	    return Jsoup.clean(s, "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
	}
	
	/**
	 * getTimeFromPttURL
	 * @param url
	 * @return
	 */
	public static Date getTimeFromPttURL(String url) throws Exception {
		Matcher m = URLTimePattern.matcher(url);
		if (m.find()) {
			long timestamp = Long.parseLong(m.group("timestamp"));
			return timestamp2Date(timestamp);
		}
		return null;
	}
	
	/**
	 * Convert Unix timestamp to Date
	 * @param unixSeconds
	 * @return
	 */
	public static Date timestamp2Date(long unixSeconds) {
		return new Date(unixSeconds * 1000L);
	}
	
}
