module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  testTimeout: 20000,
  transform: {
    "^.+\\.tsx?$": [
      "ts-jest",
      {
        tsconfig: "tsconfig.json"
      }
    ]
  },
  verbose: true,
  moduleFileExtensions: ["ts", "tsx", "js", "json"],
  testMatch: ["**/?(*.)+(spec|test).[tj]s?(x)"]
};
