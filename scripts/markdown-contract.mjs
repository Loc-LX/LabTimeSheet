/** Removes fenced code while retaining real document text (GOV-016). */
function prose(text) {
  let fence = null;
  return text.split(/\r?\n/).filter((line) => {
    const marker = line.match(/^ {0,3}(`{3,}|~{3,})/);
    if (marker) {
      if (!fence) fence = marker[1];
      else if (marker[1][0] === fence[0] && marker[1].length >= fence.length) fence = null;
      return false;
    }
    return !fence;
  }).join("\n");
}

/** Extracts ATX headings and explicit HTML IDs used by repository Markdown (GOV-016). */
export function markdownAnchors(text) {
  const body = prose(text);
  const anchors = new Set([...body.matchAll(/<a\b[^>]*\b(?:id|name)=["']([^"']+)["'][^>]*>/g)].map((m) => m[1]));
  const headings = new Set();
  for (const match of body.matchAll(/^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$/gm)) {
    const base = match[1].replace(/<[^>]+>/g, "").toLowerCase()
      .replace(/[^\p{L}\p{N}\p{M}_\- ]/gu, "").replaceAll(" ", "-");
    let anchor = base;
    for (let suffix = 1; headings.has(anchor); suffix += 1) anchor = `${base}-${suffix}`;
    headings.add(anchor);
    anchors.add(anchor);
  }
  return anchors;
}

/** Extracts inline Markdown destinations outside code examples (GOV-016). */
export function markdownLinks(text) {
  const body = prose(text).replace(/(`+)[\s\S]*?\1/g, "");
  return [...body.matchAll(/\]\((?:<([^>]+)>|([^\s)]+))(?:\s+"[^"]*")?\)/g)]
    .map((match) => match[1] ?? match[2]);
}
