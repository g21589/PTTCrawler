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

    java -jar PTTCrawler.jar -u Username -p Password -b Gossiping [-m]

which `Username` and `Password` are your PTT account and password to login PTT.  
Use `-m` flag to enable multi-thread.  
注意: 在文章編號大於十萬的看版，例如八卦版(Gossiping)，請在`個人化設定`中啟用`使用新式簡化游標`使文章編號不被全型的`●`所覆蓋。

Version
----

0.9.7

TODO
----
* Analysis the post content to structured data.  
* Support multi boards list

License
----

MIT

[license-image]: http://img.shields.io/badge/license-MIT-blue.svg?style=flat
[license-url]: LICENSE
