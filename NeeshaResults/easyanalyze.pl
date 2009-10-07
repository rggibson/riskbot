#!/usr/bin/perl

$part = 0;
@players = (0,0,0);
$count = 0;
@wins = (0,0,0);
$i = 0;
$ordered = "Ordered";
while ($i < 7) {
	$filename = "results" . $i . $ordered . ".txt";

	print $filename . "\n";

	open (MYFILE, $filename);
	while (<MYFILE>) {
		chomp;
		if ($_ == 0 && $count < 1000) {
			$wins[0]++;
		}
		if ($_ == 1 && $count < 1000) {
			$wins[1]++;
		}
		if ($_ == 2 && $count < 1000) {
			$wins[2]++;
		}
		$count++;	

	}
	close (MYFILE); 


	print "Player 0 won " . $wins[0] . "\n";
	print "Player 1 won " . $wins[1] . "\n";
	print "Player 2 won " . $wins[2] . "\n";
	
	if ($ordered eq "Ordered") {
		print "Ordered results:\n";
		$ordered = "Unordered";
		$part = 0;
		@players = (0,0,0);
		$count = 0;
		@wins = (0,0,0);
	} else {
		print "Unordered results:\n";
		$ordered = "Ordered";
		$i++;
		$part = 0;
		@players = (0,0,0);
		$count = 0;
		@wins = (0,0,0);

	}

	


}