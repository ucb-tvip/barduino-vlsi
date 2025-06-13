function [counter] = lo_counter(adc_clock_freq, num_adc_clock_cycles, f_lo)
    counting_period = (1/adc_clock_freq) * num_adc_clock_cycles;
    lo_period = 1/f_lo;
    counter = floor(counting_period/lo_period);
    fprintf("Counting period: %.15f\n", counting_period);
    fprintf("LO period: %.15f\n", lo_period);
end
