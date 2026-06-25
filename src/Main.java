import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.ArrayList;

public class Main {

	public static void main(String[] args) {
		//////////////////////////////////////////////
		// CONVEX HULL DIVIDE-AND-CONQUER ALGORITHM //
		//////////////////////////////////////////////
		
		
		//INPUT: a .csv file with (x,y) coordinate pairs
		//data already sorted by x-coordinate
		
		//store data in an ArrayList coordinatesList, not split or delimited 
		String filePath = "C:\\Users\\anddr\\Documents\\_UTSA\\_Design and Analysis of Algorithms\\Projects\\Project1\\Project1\\input.csv";
		ArrayList<String> coordinatesList = new ArrayList<>();
		try (Scanner scanner = new Scanner(new File(filePath))){
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				coordinatesList.add(line);
//				System.out.println(line);
			}
		} catch (FileNotFoundException e) {
			System.err.println("The file could not be found: " + e.getMessage());
		}
//		for (String coordinate : coordinatesList) {
//			System.out.println(coordinate);
//		}
		
		
		//divide the set of points into two "halves" A and B according to x-coordinate
		
		ArrayList<String> leftList = new ArrayList<>();
		ArrayList<String> rightList = new ArrayList<>();
		leftList coordinatesList;
		
		
		//recursively compute the convex hull of A, and recursively compute the convex hull of B
		
		//merge the two convex hulls to get the convex hull of A union B
		//merging: compute upper and lower tangent to the convex hulls in O(n) time
		
		
		
		////////////////
		// PSEUDOCODE //
		////////////////

		
//		ConvexHull(P,l,r)
//		{
//			//check base case
//			if (r - l <= 2)
//			{
//				return convex hull for the 2 or 3 point subproblem
//			}
//			mid = (l + r) / 2
//			leftCH = ConvexHull(P, l, mid)
//			rightCH = ConvexHull(P, mid+1, r)
//			
//			let a_upper and b_upper be upper tangent points
//			let a_lower and b_lower be lower tangent points
//			
//			return merged convex hull:
//				1) b_upper to a_upper
//				2) follow leftCH from a_upper to a_lower in CCW order
//				3) a_lower to b_lower
//				4) follow rightCH from b_lower to b_upper
//		}
		
		
	}
}