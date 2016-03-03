package crawler.base;

import java.util.Date;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class Reply {
	
	public enum ReplyType {
		Positive, Negative, Normal
	};
	
	private String ID = null;
	private Date postTime = null;
	private ReplyType type = null;
	private String content = null;
	
	public Reply() {
		
	}
	
	public Reply(DBObject obj) {
		this.ID = (String) obj.get("ReplyID");
		this.type = (ReplyType) obj.get("ReplyType");
		this.content = (String) obj.get("ReplyContent");
		this.postTime = (Date) obj.get("ReplyDate");
	}
	
	public String getID() {
		return ID;
	}

	public Reply setID(String ID) {
		this.ID = ID;
		return this;
	}

	public Date getPostTime() {
		return postTime;
	}

	public Reply setPostTime(Date postTime) {
		this.postTime = postTime;
		return this;
	}

	public ReplyType getType() {
		return type;
	}

	public Reply setType(ReplyType type) {
		this.type = type;
		return this;
	}

	public String getContent() {
		return content;
	}

	public Reply setContent(String content) {
		this.content = content;
		return this;
	}
	
	public DBObject toMongoDBObject() {
		DBObject docReply = new BasicDBObject();
		docReply.put("ReplyID", ID);
		String rt = null;
		if (type == ReplyType.Positive) {
			rt = "UpvoteCount";
		} else if (type == ReplyType.Negative) {
			rt = "DownvoteCount";
		} else if (type == ReplyType.Normal) {
			rt = "NeutralCount";
		}
		docReply.put("ReplyType", rt);
		docReply.put("ReplyContent", content);
		docReply.put("ReplyDate", postTime);
		return docReply;
	}
	
	public static Reply fromMongoDBObject(DBObject obj) {
		Reply reply = new Reply();
		reply.ID = (String) obj.get("ReplyID");
		reply.type = (ReplyType) obj.get("ReplyType");
		reply.content = (String) obj.get("ReplyContent");
		reply.postTime = (Date) obj.get("ReplyDate");
		return reply;
	}
	
}
