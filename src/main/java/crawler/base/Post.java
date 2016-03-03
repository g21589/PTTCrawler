package crawler.base;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class Post {
	
	private static final Logger log = LoggerFactory.getLogger(Post.class);
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private String ID = null;
	private String title = null;
	private String author = null;
	private String status = null;
	private String karma = null;
	private Date postTime = null;
	private String url = null;
	
	private int upVoteCount = 0;
	private int downVoteCount = 0;
	private int neutralCount = 0;
	
	private String content = null;
	
	private ArrayList<Reply> replies = null;
	
	public Post() {
		replies = new ArrayList<Reply>();
	}
	
	public Post(DBObject obj) {
		this.ID = (String) obj.get("ID");
		this.author = (String) obj.get("Artist");
		this.title = (String) obj.get("Title");
		this.postTime = (Date) obj.get("postTime");
		this.url = (String) obj.get("Url");
		this.content = (String) obj.get("Content");
		Set<String> keys = obj.keySet();
		int replyCount = keys.size() - 7;
		this.replies = new ArrayList<Reply>(replyCount);
		for (int i=0; i < replyCount; i++) {
			DBObject replyObj = (DBObject) obj.get(String.format("reply %d/%d", i+1, replyCount));
			this.replies.add(new Reply(replyObj));
		}
	}
	
	public DBObject toMongoDBObject() {
		DBObject docPost = new BasicDBObject();
		docPost.put("ID", ID);
		docPost.put("Artist", author);
		docPost.put("Title", title);
		docPost.put("postTime", postTime);
		docPost.put("Url", url);
		docPost.put("UpvoteCount", upVoteCount);
		docPost.put("DownvoteCount", downVoteCount);
		docPost.put("NeutralCount", neutralCount);
		docPost.put("catchTime", new Date());
		docPost.put("Content", content);
		for (int i=0; i<replies.size(); i++) {
			docPost.put(String.format("reply %d/%d", i+1, replies.size()), replies.get(i).toMongoDBObject());
		}
		return docPost;
	}
	
	public static Post fromMongoDBObject(DBObject obj) {
		
		Post post = new Post();
		post.ID = (String) obj.get("ID");
		post.author = (String) obj.get("Artist");
		post.title = (String) obj.get("Title");
		post.postTime = (Date) obj.get("postTime");
		post.url = (String) obj.get("Url");
		post.content = (String) obj.get("Content");
		post.upVoteCount = (int) obj.get("UpvoteCount");
		post.downVoteCount = (int) obj.get("DownvoteCount");
		post.neutralCount = (int) obj.get("NeutralCount");
		Set<String> keys = obj.keySet();
		int replyCount = keys.size() - 12;
		post.replies = new ArrayList<Reply>(replyCount);
		for (int i=0; i < replyCount; i++) {
			DBObject replyObj = (DBObject) obj.get(String.format("reply %d/%d", i+1, replyCount));
			post.replies.add(new Reply(replyObj));
		}
		
		return post;
	}
	
	/**
	 * 依據時間只截取出更新部分
	 * @param fromDate
	 * @param toDate
	 * @return
	 */
	public Post extractUpdatePost(Date fromDate, Date toDate) {
		// Content設為null
		this.setContent(null);
		
		// 重新統計回應的種類數
		int diffUpvoteCount = 0, diffDownvoteCount = 0, diffNeutralCount = 0;
		ArrayList<Reply> allReplies = this.getReplies();
		ArrayList<Reply> newReplies = new ArrayList<Reply>();
		for (Reply reply : allReplies) {
			Date pt = reply.getPostTime();
			if (pt != null && !pt.before(fromDate) && pt.before(toDate)) {
				switch (reply.getType()) {
				case Positive:
					++diffUpvoteCount;
					break;
				case Negative:
					++diffDownvoteCount;
					break;
				case Normal:
					++diffNeutralCount;
					break;
				default:
					break;
				}
				newReplies.add(reply);
			}
		}
		this.setUpVoteCount(diffUpvoteCount);
		this.setDownVoteCount(diffDownvoteCount);
		this.setNeutralCount(diffNeutralCount);
		this.setReplies(newReplies);
		
		if (newReplies.size() > 0) {
			log.info(String.format("Update post (+: %d, -: %d, ~: %d)", diffUpvoteCount, diffDownvoteCount, diffNeutralCount));
		}
		
		return this;
	}
	
	public String toString() {
		return String.format("#%s  %s  %-13s %s", this.ID, sdf.format(this.postTime), this.author, this.title);
	}
	
	public String getID() {
		return ID;
	}

	public Post setID(String ID) {
		this.ID = ID;
		return this;
	}
	
	public String getTitle() {
		return title;
	}

	public Post setTitle(String title) {
		this.title = title;
		return this;
	}
	
	public String getAuthor() {
		return author;
	}

	public Post setAuthor(String author) {
		this.author = author;
		return this;
	}

	public String getStatus() {
		return status;
	}

	public Post setStatus(String status) {
		this.status = status;
		return this;
	}

	public String getKarma() {
		return karma;
	}

	public Post setKarma(String karma) {
		this.karma = karma;
		return this;
	}

	public Date getPostTime() {
		return postTime;
	}

	public Post setPostTime(Date postTime) {
		this.postTime = postTime;
		return this;
	}
	
	public String getUrl() {
		return url;
	}

	public Post setUrl(String url) {
		this.url = url;
		return this;
	}
	
	public String getContent() {
		return content;
	}

	public Post setContent(String content) {
		this.content = content;
		return this;
	}

	public ArrayList<Reply> getReplies() {
		return replies;
	}

	public Post setReplies(ArrayList<Reply> replies) {
		this.replies = replies;
		return this;
	}
	
	public void addReply(Reply reply) {
		replies.add(reply);
	}
	
	public Reply getReply(int index) {
		return replies.get(index);
	}

	public int getUpVoteCount() {
		return upVoteCount;
	}

	public Post setUpVoteCount(int upVoteCount) {
		this.upVoteCount = upVoteCount;
		return this;
	}

	public int getDownVoteCount() {
		return downVoteCount;
	}

	public Post setDownVoteCount(int downVoteCount) {
		this.downVoteCount = downVoteCount;
		return this;
	}

	public int getNeutralCount() {
		return neutralCount;
	}

	public Post setNeutralCount(int neutralCount) {
		this.neutralCount = neutralCount;
		return this;
	}
	
}
