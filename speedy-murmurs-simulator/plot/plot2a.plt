#!/usr/bin/gnuplot -persist

# set key top left
set title "Fig 2a"
set xlabel "Trees"
set ylabel "Success Ratio"
set xrange [0:8]
set yrange [0:1]
set pointsize 1
set grid
plot "fig2a_vals.txt" using 1:2:xtic(1) with linespoints title "SpeedyMurmurs","fig2a_vals.txt" using 1:3:xtic(1) with linespoints title "SilentWhispers"
