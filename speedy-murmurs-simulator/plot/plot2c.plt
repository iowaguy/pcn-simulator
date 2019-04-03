#!/usr/bin/gnuplot -persist

set title "Fig 2c"
set xlabel "Attempts"
set ylabel "Success Ratio"
set xrange [0:10]
set yrange [0:1]
set pointsize 1
set grid
plot "fig2c_vals.txt" using 1:2:xtic(5) with linespoints title "SpeedyMurmurs","fig2c_vals.txt" using 1:3:xtic(5) with linespoints title "SilentWhispers"
