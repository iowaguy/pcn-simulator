#!/usr/bin/gnuplot -persist

set title "Fig 2c"
set xlabel "Attempts"
set ylabel "Success Ratio"
set grid
plot "fig2c_vals.txt" u (column(0)):2:xtic(1) w l title "","fig2c_vals.txt" u (column(0)):3:xtic(1) w l title ""

