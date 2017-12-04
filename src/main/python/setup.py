from setuptools import setup, find_packages

with open('README.rst') as f:
    readme = f.read()

setup(
    name='fjagepy',
    version='1.0.0b1',
    description='Fjage Python Gateway',
    long_description=readme,
    author='Subnero Pte. Ltd.',
    author_email='prasad@subnero.com',
    url='https://github.com/org-arl/fjage/tree/dev/src/main/python',
    license='BSD (3-clause)',
    python_requires='>=3',
    classifiers=[
        'Development Status :: 4 - Beta',
        'Programming Language :: Python :: 3.5',
        'Programming Language :: Python :: 3.6',
    ],
    packages=find_packages(exclude=('tests', 'docs')),
)
