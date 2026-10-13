"use strict";

const prompt = process.argv[2] || "";
if (/username/i.test(prompt)) {
  process.stdout.write(process.env.LOCAL_GIT_ASK_USER || "git");
} else {
  process.stdout.write(process.env.LOCAL_GIT_ASK_TOKEN || "");
}
