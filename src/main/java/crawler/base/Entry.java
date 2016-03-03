package crawler.base;

public class Entry {
	
	public String id = null;
	public String number = null;
	public String status = null;
	public String karma = null;
	public String date = null;
	public String author = null;
	public String url = null;
	public String title = null;
	
	public Entry() {
		
	}
	
	public Entry(String id, String number, String status, String karma, String date, String author, String title, String url) {
		this.id = id;
		this.number = number;
		this.status = status;
		this.karma = karma;
		this.date = date;
		this.author = author;
		this.url = url;
		this.title = title;
	}
	
	public String toFullString() {
		return String.format("ID: %8s  Num: %-5s  Status: %1s  Karma: %-2s  Date: %5s  Author: %-13s Title: %s", id, number, status, karma, date, author, title);
	}
	
	public String toString() {
		return String.format("#%8s %5s %5s %-13s %s", id, number, date, author, title);
	}
	
}
