# Knact Core

This module contains traits and classes for an extensible monitoring system.

The main focus is the `Watchdog` class which is the only concrete class. The class is responsible 
for sending out commands periodically for a list of nodes that could grow or shrink in size over 
time. The type `Observable[_]` is used to model any data that changes over time.