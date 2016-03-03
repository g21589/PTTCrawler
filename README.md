PTTCrawler
==========
[![MIT License][license-image]][license-url]

PTTCrawler is a post crawler in PTT board. PTTCrawler is implemented by Java.  

Features
----
* It supports **telnet** (by Apache commons-net) and **SSH** (by JSch) protocols to connect to ptt.  
* It renders the **VT100 terminal** screen to crawl original posts.  
* Connect Ptt by **UTF-8** character set.  
* Support *multi-thread* crawl posts.  
* [API] Also support web version to download the Ptt post.

How to use
----
If we want to crawl all posts in the `Gossiping` board, use the following command:

    java -jar PTTCrawler.jar -u [Username] -p [Password] -b [Gossiping]

which `Username` and `Password` are your PTT account and password to login PTT.

Version
----

0.9.6

TODO
----
* Analysis the post content to structured data.

License
----

MIT

[license-image]: http://img.shields.io/badge/license-MIT-blue.svg?style=flat
[license-url]: LICENSE
