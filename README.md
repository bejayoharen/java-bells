Java Bells
============

A Jingle implementation for Java based on LibJitsi, Ice4J and Smack

Jitsi implements many features that would be desirable to add to an XMPP library, including
Jingle and ICE. Unfortunately, these features are not easily accessable to developers
nor are they well documented.
This project aims to make these features available to developers with good sample code
and decent documentation.

This library is not yet functional and is, frankly, a bit of a mess, but we're getting there.


Compiling and Running
-------------------

To compile and run, you can use the included ant build.xml file. All the required libraries
are included with the distrobution. The compile target compiles the code and the test target
compiles and runs a test. ATM the test is incomplete and requires you to hit return to complete.


Source Overview
---------------

The main functionality for XMPP connections is provided by the Smack library. In many ways, Java
Bells is designed to replace the obsolete smacks-jingle package. ICE functionality is provided by
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
- Create a new JinglePacketManager for each connection. You'll want to override the createJingleSession
  method to create and return a JingleSession object that behaves the way you want. It is important to create
  your own JingleSession (usually by subclassing DefaultJingleSession) because this is the class that
  makes decisions about, for example, how to respond to incoming calls.

