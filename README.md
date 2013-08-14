Java Bells
============

A Jingle implementation for Java based on LibJitsi, Ice4J and Smack

Jitsi implements many features that would be desirable to add to an XMPP library, including
Jingle and ICE. Unfortunately, these features are not easily accessable to developers
nor are they well documented.
This project aims to make these features available to developers with good sample code
and decent documentation.


Status
------

Basic functionality is implemented for both calling out and receiving calls. The receiver can
accept calls from other XMPP/Jingle apps like Jitsi. Some documentation,
cleanup and testing is still required. The API may change.


Project Goals
-------------

The goals of this project are:

- to create an easy-to-use method of adding audio and video chat functionality to any java app.
- to not require developers to have more than a cursory understanding of Jingle or ICE.
- to not modify third-party code or libraries, so that this code can be kept up-to-date easily.

It is also desirable to minimize external requirements, but this is less important.

Compiling and Running
-------------------

To compile and run, you can use the included ant build.xml file. All the required libraries
are included with the distrobution. The following targets are available:
- **compile** compiles the code
- **jar** builds the necessary jars for use in other code
- **clean** cleans for a fresh build
- **test** runs a test, trying to both call and answer. It is better to use the testcall and testanswer tests seperately.
- **testanswer** connects to the XMPP server and waits to be "Called"
- **testcall** connects to the XMPP server and calls the answerer
- **testanswer2** newer version of testanswer with slightly different style.
- **testcall2** newer version of testcall with slightly different style.
- **testcallout** tests calling out to an external system like jitsi.

For the tests to run, you will need to copy the passwords.props.template to passwords.props and fill
in the values. Don't commit passwords.props to the repository.

ToDo
----

- There seem to still be some issues with ICE: eg right now connections don't always work on the same subnet.
- Demonstrate selecting input devices and screen sharing.
- Respond to transport-info including ice-restarts
- Disconnect when other party disconnects from xmpp
- Respond to error messages sent from peer
- There are some issues with finding the stun and turn DNS servers. Falling back on jitsi.org servers for now.

How You Can Help
----------------

It is surprisingly easy to help. In order from easy to hard:

- Star or watch this project. That will make it more popular. If you think this project is
or might be helpful to you in the future, please star it!
- Try it out and report problems. You can report issues via github or on the jitsi dev mailing
list. If you use the jitsi dev mailing list, be sure to include Java-Bells in the subject.
- Submit patches/pull requests. This is the best way to ensure issues are fixed!


Source Overview
---------------

The main functionality for XMPP connections is provided by the Smack library. In many ways, Java
Bells is designed to replace the obsolete smacks-jingle package that came with smack.
ICE functionality is provided by
the Ice4J library, and the actual media IO and media streaming is provided by LibJitsi. Because
LibJitsi and Ice4J have little documentation, other than the overwhelming Jitsi soucecode, it is
difficult to inplement these features in other code. This library provides both lightwieght
code to help make it easy to use the functions in Ice4J and LibJitsi as well as sample code to
demonstrate how to do so.

The sample code is in the package com.xonami.javaBellsSample. Normally you will not use this package
in your own code, but rather use it as an example. The code for this library is in the package
com.xonami.javaBells. Other packages are coppied from Jitsi.


To use Java Bells, here are the basic steps you need to follow. See the JavaBellsSample for more detail:

- Call LibJitsi.start() (You will also want a corresponding LibJitsi.stop() at the end of your application.)
- Call JingleManager.enableJingle() (This ensures that smack won't automatically reject all jingle packets
  for you, and enabled automatic creation of jingleIQ packets.)
- Create a new JinglePacketHandler for each connection. You'll want to override the createJingleSession
  method to create and return a JingleSession object that behaves the way you want. It is important to create
  your own JingleSession (usually by subclassing DefaultJingleSession) because this is the class that
  makes decisions about, for example, how to respond to incoming calls.
- See the sample code for more details.
