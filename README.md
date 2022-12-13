# Wcofun.net Downloader

The project name is a little misleading. The original website seems to change all the time, but it's too late to change it.

This program is used to download videos and series from https://www.wcofun.net/

If you check that website out, it's one of the best video sites for free cartoons and anime.

First off you are wondering if this website is illegal? Yes it sure is.

The thing is you won't get in trouble for using it or downloading from it. It is not like torrents because no one can track you, so don't worry.

Some countries do block it though, so use a VPN of your choice before downloading anything.

I suggest https://protonvpn.com because it is free and fast.

# Requirements

You need java 8, 9 or 10 to run this program. Java 8 is the most compatible.

Anything higher than Java 10 or incomplete OpenJDK packages won't work due to the lack of an important library: Java FX

I suggest downloading Java 8 directly from Oracle. Note: You need an account. https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html
> If you are not going to edit the program, you just need the Java Runtime Environment else you can get the Java Development Kit which comes with the JRE.

If you don't want to sign up for anything, you can use Liberica instead: https://bell-sw.com/pages/downloads/#downloads
> Liberica is just another branch of OpenJDK, but it is managed better.

Make sure you choose the Full JRE or Full JDK for development:
> Anything else doesn't include Java FX.

![Alt text](images/liberica.png?raw=true "Liberica")

If you're editing the program you will need an IDE.

I use IntelliJ as an IDE: https://www.jetbrains.com/idea/download/ - The Community Version will work just fine.

You will also need a browser of your choice.

This program works with: Chrome, Chromium, Opera, Edge, Firefox or Safari.
> You must install the browser. A portable version will not work.
> Updating the browser to it's latest version is recommended.

This program relies heavily on Selenium: https://www.selenium.dev 
> Selenium allows this program to scrape website data with javascript enabled. Unfortunately we need javascript so selenium is crucial.

The driver for the chosen browser will automatically download with the help of: https://github.com/bonigarcia/webdrivermanager

Download the latest release here: https://github.com/NobilityDeviant/Wcofun.com_Downloader/releases/

You can download the jar file only, but I suggest getting the full package.

If double clicking on the jar file doesn't run the app, you will need to execute a command.

Inside your CMD or Terminal, navigate the the jad and type:

> java -jar TWCD.jar

and press enter.

You can usually hold shift and right click inside the folder where jar is to open the CMD or Terminal in that location.

![Alt text](images/home.png?raw=true "Home Tab")

A lot of work was put into this one!
I manually downloaded so many videos from wco that I decided to create this bad boy.

This downloader is multi-threaded, customizable, resizeable, fast and easy to use!
Utilizes Kotlin's coroutines for a fast and smooth experience.

You just type a link to a series or an episode such as one of my favorites: https://www.wcofun.net/anime/to-love-ru
inside the textfield and hit ENTER or press Start

# Downloads

All past and current downloads will go inside this organized tab here:

![Alt text](images/downloads.png?raw=true "Download Tab")

You can interact with the downloads by right clicking any entry.

Every download will go inside their respective Series folder to keep it organized. 
Just select the main download folder in the settings. The default one is your home folder.

# Settings

![Alt text](images/settings.png?raw=true "Settings Window")

You can now choose the browser you want to use with selenium.
Be sure to select it before downloading anything. You must have the chosen browser installed.
You can adjust your settings by going to Settings > Open Settings in the menu bar.

# Series Details

![Alt text](images/seriesdetails.png?raw=true "Series Details WIndow")

With the new cache system, you don't even need to visit wco to check out a series details!

# Download Confirm

![Alt text](images/downloadconfirm.png?raw=true "Download Confirm Window")

You are now able to select all the episodes inside a series you want to download!
It feels so much better than the max episodes option.

# WCO Database

![Alt text](images/wco.png?raw=true "WCO Window")

Now as an optional download, you can view ALL the series inside wcofun all from this simple window.
It has the same categories (minus OVA because it's just in dubbed/subbed) and is so much easier to use.
You can even use the search bar to filter through a keyword, or genre.
Why doesn't wco have images next to their links? IDK but that's why I created this.

# Recent

![Alt text](images/recent.png?raw=true "Recent Window")

Now inside the wco window, you can view the latest episodes and series displayed on the wco home page.
Just as a little QoL feature.
Right click any entry to see the options.

NOTE: This window isn't very optimized. It can be quite slow due to the big amount of data.
I suggest not using the columns to sort and only use the search bar. :)

Maybe one day I'll use Compose for the UI.

Enjoy and have fun!

Known Issue: This program does not support downloading 2 videos from one page.
Idk why this website has them, but it's best to ignore them for now.

# Donate

Find my projects useful? Consider donating as it will motivate me to create more free projects. :)

#Removed. Will keep this off for now.


