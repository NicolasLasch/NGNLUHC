#!/bin/bash
# Define ANSI colors
BOLD="\033[1m"
RESET="\033[0m"
CYAN="\033[1;36m"
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
WHITE="\033[1;37m"

# Print the header
printf "${CYAN}${BOLD}%-25s %-10s %-10s %-10s %-12s %-10s %-15s %-15s %-15s${RESET}\n" "Author" "Added" "Removed" "Total" "Net Change" "Commits" "Avg Change" "First Commit" "Last Commit"
printf "${CYAN}%-25s %-10s %-10s %-10s %-12s %-10s %-15s %-15s %-15s${RESET}\n" "-------------------------" "----------" "----------" "----------" "------------" "---------" "--------------" "---------------" "--------------"

# Temp file for sorting
tmpfile=$(mktemp)

# Loop through authors
git log --pretty="%aN" | sort -u | while read name; do
  read added removed total <<< $(git log --author="$name" --pretty=tformat: --numstat | \
    awk "{ add += \$1; subs += \$2 } END { printf \"%s %s %s\", add, subs, add + subs }")

  net_change=$((added - removed))
  commits=$(git log --author="$name" --pretty=oneline | wc -l)

  if [ "$commits" -gt 0 ]; then
    avg_change=$(echo "scale=2; $net_change / $commits" | bc)
  else
    avg_change="0.00"
  fi

  if [ "$net_change" -gt 0 ]; then
    net_color=$GREEN
  elif [ "$net_change" -lt 0 ]; then
    net_color=$RED
  else
    net_color=$YELLOW
  fi

  first_date=$(git log --author="$name" --reverse --pretty="%ad" --date=short | head -n 1)
  last_date=$(git log --author="$name" --pretty="%ad" --date=short | head -n 1)

  # Save the raw line to temp file for sorting (prepend last_date for sorting)
  echo "$last_date|$name|$added|$removed|$total|$net_change|$commits|$avg_change|$first_date|$last_date" >> "$tmpfile"
done

# Sort and print
sort -r "$tmpfile" | while IFS="|" read last name added removed total net commits avg first last; do
  printf "${WHITE}%-25s ${GREEN}%-10s ${RED}%-10s ${CYAN}%-10s ${GREEN}%-12s ${YELLOW}%-10s ${CYAN}%-15s ${RESET}%-15s %-15s\n" "$name" "$added" "$removed" "$total" "$net" "$commits" "$avg" "$first" "$last"
done

rm "$tmpfile"