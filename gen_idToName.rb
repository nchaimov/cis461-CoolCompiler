$stderr.puts "Creating Util.java"

puts "public class Util {
\tpublic static String idToName(int id) {
\t\tswitch(id) {"
File.open("sym.java", "r") do |file|
  file.each do |line|
    line =~ /public static final int (.*?) =/
    puts "\t\t\tcase sym.#{$1}:\n\t\t\t\treturn \"#{$1}\";" unless $1 == nil
  end
end
File.open("Nodes.java", "r") do |file|
  file.each do |line|
    line =~ /public static final int (.*?) =/
    puts "\t\t\tcase Nodes.#{$1}:\n\t\t\t\treturn \"#{$1}\";" unless $1 == nil
  end
end
puts "\t\t\tdefault: throw new IllegalArgumentException(\"Unknown token\");
    \t\t}
  \t}
}"