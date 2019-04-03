#!/usr/bin/gnuplot -persist

set title "Fig 2b"
set xlabel "Trees"
set ylabel "(Hop)Delay"
set xrange [0:8]
set yrange [0:30]
set pointsize 1
set grid
plot "fig2b_vals.txt" using 1:2:xtic(5) with linespoints title "SpeedyMurmurs","fig2b_vals.txt" using 1:3:xtic(5) with linespoints title "SilentWhispers"
