module.exports = {
preset: 'ts-jest',
testEnvironment: 'node',
testMatch: ['**/src/tests/**/*.test.ts'],
collectCoverage: true,
coverageDirectory: 'coverage',
};